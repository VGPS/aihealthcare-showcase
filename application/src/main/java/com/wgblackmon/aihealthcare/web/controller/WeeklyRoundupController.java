package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.web.util.ArticleToneClassifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saturday weekly roundup post generator for LinkedIn and Substack.
 *
 * <p>Fetches the top 10 articles from the past 7 days filtered to COMPETITOR
 * and LEGAL source tiers, generates topic summaries, and produces
 * platform-specific copy-ready text blocks:
 * <ul>
 *   <li><strong>LinkedIn post body</strong> — categorized headlines with tone
 *       emojis, topic summaries, no links (reach penalty avoidance)</li>
 *   <li><strong>LinkedIn first comment</strong> — numbered source URLs</li>
 *   <li><strong>Substack article</strong> — long-form markdown-style article
 *       with inline links, suitable for direct paste into the Substack editor</li>
 * </ul>
 *
 * <p>No LLM calls; classification uses keyword matching via
 * {@link ArticleToneClassifier} and sourceTier field filtering.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-19
 * @updated 2026-09-19
 */
@Slf4j
@Controller
public class WeeklyRoundupController {

    private static final int MAX_ARTICLES = 10;
    private static final int LOOKBACK_DAYS = 7;
    private static final int SNIPPET_MAX_CHARS = 180;
    private static final int POST_BODY_LIMIT = 2900;
    private static final int LINKS_BLOCK_LIMIT = 1050;

    private static final DateTimeFormatter DATE_FMT = DisplayFormats.LONG_DATE;
    private static final String SITE_URL = "https://app.bigskylabs.ai";
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");

    private static final Set<String> TARGET_TIERS = Set.of("LEGAL", "COMPETITOR");

    private static final String[] LEGAL_KEYWORDS = {
        "lawsuit", "litigation", "court", "class action", "settlement",
        "ftc", "antitrust", "doj", "attorney general", "sec filing",
        "hipaa violation", "patent infringement", "copyright infringement",
        "criminal charges", "indictment", "legal challenge", "legal action",
        "injunction", "verdict", "subpoena", "regulatory fine", "penalty",
        "fda clears", "fda approves", "fda guidance", "fda draft",
        "cms rule", "cms proposes", "legislation", "signed into law",
        "proposed rule", "final rule", "rulemaking", "state law", "regulation"
    };

    private final ArticleIngestionPort  articleIngestionPort;
    private final ArticleToneClassifier toneClassifier;

    public WeeklyRoundupController(ArticleIngestionPort articleIngestionPort,
                                   ArticleToneClassifier toneClassifier) {
        log.debug("WeeklyRoundupController() | articleIngestionPort={}, toneClassifier={}",
                articleIngestionPort, toneClassifier);
        this.articleIngestionPort = articleIngestionPort;
        this.toneClassifier      = toneClassifier;
    }

    /**
     * Renders the weekly roundup post generator page.
     *
     * @param model Thymeleaf model
     * @return the "weekly-roundup" view name
     */
    @GetMapping("/dashboard/weekly-roundup")
    public String weeklyRoundup(Model model) {
        log.debug("weeklyRoundup() | entry");

        List<NewsArticle> raw = articleIngestionPort.fetchRecentArticles(LOOKBACK_DAYS);
        List<NewsArticle> tierFiltered = filterByTier(raw);
        List<NewsArticle> usable = filterUsable(tierFiltered);
        List<NewsArticle> sorted = sortByTierThenWeight(usable);
        List<NewsArticle> deduped = deduplicateByTitle(sorted);
        List<NewsArticle> top = limitList(deduped, MAX_ARTICLES);

        int legalCount = countByTier(top, "LEGAL");
        int competitorCount = countByTier(top, "COMPETITOR");

        LocalDate today = LocalDate.now(CHICAGO);
        LocalDate weekStart = today.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        String dateLabel = DATE_FMT.format(weekStart) + " – " + DATE_FMT.format(today);

        String legalSummary = buildLegalSummary(top);
        String competitorSummary = buildCompetitorSummary(top);

        String linkedinBody = buildLinkedInBody(top, dateLabel, legalSummary, competitorSummary);
        String linkedinComment = buildLinkedInComment(top);
        String substackArticle = buildSubstackArticle(top, dateLabel, legalSummary, competitorSummary);

        model.addAttribute("dateLabel", dateLabel);
        model.addAttribute("articleCount", top.size());
        model.addAttribute("legalCount", legalCount);
        model.addAttribute("competitorCount", competitorCount);
        model.addAttribute("linkedinBody", linkedinBody);
        model.addAttribute("linkedinBodyLength", linkedinBody.length());
        model.addAttribute("linkedinComment", linkedinComment);
        model.addAttribute("linkedinCommentLength", linkedinComment.length());
        model.addAttribute("substackArticle", substackArticle);
        model.addAttribute("substackArticleLength", substackArticle.length());
        model.addAttribute("articles", top);

        log.debug("weeklyRoundup() | return=weekly-roundup, articles={}, legal={}, competitor={}",
                top.size(), legalCount, competitorCount);
        return "weekly-roundup";
    }

    private List<NewsArticle> filterByTier(List<NewsArticle> articles) {
        log.debug("filterByTier() | articles={}", articles.size());
        List<NewsArticle> result = new ArrayList<>();
        for (NewsArticle a : articles) {
            if (a.sourceTier() != null && TARGET_TIERS.contains(a.sourceTier().toUpperCase())) {
                result.add(a);
            }
        }
        log.debug("filterByTier() | return={}", result.size());
        return result;
    }

    private List<NewsArticle> filterUsable(List<NewsArticle> articles) {
        log.debug("filterUsable() | articles={}", articles.size());
        List<NewsArticle> result = new ArrayList<>();
        for (NewsArticle a : articles) {
            if (isUsable(a)) {
                result.add(a);
            }
        }
        log.debug("filterUsable() | return={}", result.size());
        return result;
    }

    private boolean isUsable(NewsArticle a) {
        if (a.sourceWeight() < 0.5) {
            return false;
        }
        if (a.title() != null && a.title().toLowerCase().contains("google news")) {
            return false;
        }
        if (a.bodyText() != null && a.bodyText().trim().startsWith("NFE/")) {
            return false;
        }
        if (a.title() != null && a.title().contains("- Google News")) {
            return false;
        }
        String titleNorm = normalizeTitle(a.title());
        String topicNorm = normalizeTitle(a.topic());
        if (!titleNorm.isBlank() && !topicNorm.isBlank() && titleNorm.equals(topicNorm)) {
            return false;
        }
        return true;
    }

    /**
     * Sorts articles: LEGAL tier first, then COMPETITOR, within each tier by
     * sourceWeight descending. Uses keyword detection as a fallback for articles
     * where the tier label alone may not reflect legal content.
     */
    private List<NewsArticle> sortByTierThenWeight(List<NewsArticle> articles) {
        log.debug("sortByTierThenWeight() | articles={}", articles.size());
        List<NewsArticle> copy = new ArrayList<>(articles);

        for (int i = 1; i < copy.size(); i++) {
            NewsArticle key = copy.get(i);
            int keyPriority = tierPriority(key);
            int j = i - 1;
            while (j >= 0) {
                int jPriority = tierPriority(copy.get(j));
                boolean jWins = jPriority > keyPriority
                        || (jPriority == keyPriority && copy.get(j).sourceWeight() >= key.sourceWeight());
                if (jWins) {
                    break;
                }
                copy.set(j + 1, copy.get(j));
                j--;
            }
            copy.set(j + 1, key);
        }

        log.debug("sortByTierThenWeight() | return={}", copy.size());
        return copy;
    }

    private int tierPriority(NewsArticle a) {
        if ("LEGAL".equalsIgnoreCase(a.sourceTier())) {
            return 2;
        }
        if ("COMPETITOR".equalsIgnoreCase(a.sourceTier()) && hasLegalKeywords(a)) {
            return 2;
        }
        return 1;
    }

    private boolean hasLegalKeywords(NewsArticle a) {
        String text = buildSearchText(a);
        for (String kw : LEGAL_KEYWORDS) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private List<NewsArticle> deduplicateByTitle(List<NewsArticle> articles) {
        log.debug("deduplicateByTitle() | articles={}", articles.size());
        Set<String> seen = new HashSet<>();
        List<NewsArticle> result = new ArrayList<>();
        for (NewsArticle a : articles) {
            String key = normalizeTitle(a.title());
            if (!key.isBlank() && seen.add(key)) {
                result.add(a);
            }
        }
        log.debug("deduplicateByTitle() | return={}", result.size());
        return result;
    }

    private List<NewsArticle> limitList(List<NewsArticle> articles, int limit) {
        if (articles.size() <= limit) {
            return articles;
        }
        List<NewsArticle> result = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            result.add(articles.get(i));
        }
        return result;
    }

    private int countByTier(List<NewsArticle> articles, String tier) {
        int count = 0;
        for (NewsArticle a : articles) {
            if (tier.equalsIgnoreCase(a.sourceTier())) {
                count++;
            }
        }
        return count;
    }

    private String buildLegalSummary(List<NewsArticle> articles) {
        log.debug("buildLegalSummary() | articles={}", articles.size());
        List<NewsArticle> legal = new ArrayList<>();
        for (NewsArticle a : articles) {
            if ("LEGAL".equalsIgnoreCase(a.sourceTier()) || hasLegalKeywords(a)) {
                legal.add(a);
            }
        }
        if (legal.isEmpty()) {
            String result = "No significant legal or regulatory developments this week.";
            log.debug("buildLegalSummary() | return={}", result);
            return result;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("This week saw ").append(legal.size()).append(" notable legal/regulatory ");
        sb.append(legal.size() == 1 ? "development" : "developments");
        sb.append(" in AI healthcare");

        boolean hasLawsuit = false;
        boolean hasRegulation = false;
        boolean hasCompliance = false;
        for (NewsArticle a : legal) {
            String text = buildSearchText(a);
            if (text.contains("lawsuit") || text.contains("litigation") || text.contains("court")) {
                hasLawsuit = true;
            }
            if (text.contains("fda") || text.contains("cms") || text.contains("regulation")) {
                hasRegulation = true;
            }
            if (text.contains("compliance") || text.contains("hipaa") || text.contains("privacy")) {
                hasCompliance = true;
            }
        }

        List<String> themes = new ArrayList<>();
        if (hasLawsuit)    { themes.add("active litigation"); }
        if (hasRegulation) { themes.add("regulatory action"); }
        if (hasCompliance) { themes.add("compliance requirements"); }

        if (!themes.isEmpty()) {
            sb.append(", spanning ");
            for (int i = 0; i < themes.size(); i++) {
                if (i > 0 && i == themes.size() - 1) { sb.append(" and "); }
                else if (i > 0)                       { sb.append(", "); }
                sb.append(themes.get(i));
            }
        }
        sb.append(".");

        String result = sb.toString();
        log.debug("buildLegalSummary() | return={}", result);
        return result;
    }

    private String buildCompetitorSummary(List<NewsArticle> articles) {
        log.debug("buildCompetitorSummary() | articles={}", articles.size());
        List<NewsArticle> competitor = new ArrayList<>();
        for (NewsArticle a : articles) {
            if ("COMPETITOR".equalsIgnoreCase(a.sourceTier()) && !hasLegalKeywords(a)) {
                competitor.add(a);
            }
        }
        if (competitor.isEmpty()) {
            String result = "No major competitor moves tracked this week.";
            log.debug("buildCompetitorSummary() | return={}", result);
            return result;
        }

        Set<String> topics = new HashSet<>();
        for (NewsArticle a : competitor) {
            if (a.topic() != null) {
                topics.add(a.topic());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(competitor.size()).append(" competitor ");
        sb.append(competitor.size() == 1 ? "update" : "updates");
        sb.append(" tracked across ");
        sb.append(topics.size()).append(topics.size() == 1 ? " company" : " companies");
        sb.append(".");

        String result = sb.toString();
        log.debug("buildCompetitorSummary() | return={}", result);
        return result;
    }

    private String buildLinkedInBody(List<NewsArticle> articles, String dateLabel,
                                     String legalSummary, String competitorSummary) {
        log.debug("buildLinkedInBody() | articles={}, dateLabel={}", articles.size(), dateLabel);

        StringBuilder sb = new StringBuilder();
        sb.append("AI in Healthcare — Weekly Intel Roundup\n");
        sb.append(dateLabel).append("\n\n");

        sb.append("LEGAL & REGULATORY\n");
        sb.append(legalSummary).append("\n\n");
        sb.append("COMPETITOR WATCH\n");
        sb.append(competitorSummary).append("\n\n");

        if (articles.isEmpty()) {
            sb.append("No significant articles this week.\n");
        } else {
            for (int i = 0; i < articles.size(); i++) {
                NewsArticle a = articles.get(i);
                String toneEmoji = toneClassifier.toneEmoji(a);
                String tierLabel = "LEGAL".equalsIgnoreCase(a.sourceTier()) ? "[LEGAL] " : "[COMPETITOR] ";
                sb.append(i + 1).append(". ").append(toneEmoji);
                sb.append("**").append(tierLabel).append(cleanText(a.title())).append("**\n");
                String snippet = extractSnippet(a.bodyText());
                if (!snippet.isBlank()) {
                    sb.append(snippet).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("Source links in the first comment below.\n\n");
        sb.append("Follow for weekly AI healthcare intelligence.\n");
        sb.append("Full platform: ").append(SITE_URL).append("\n\n");
        sb.append(toneClassifier.linkedInHashtags(articles));
        sb.append(" #WeeklyRoundup #HealthcareLaw");

        String result = sb.toString();
        if (result.length() > POST_BODY_LIMIT) {
            result = result.substring(0, POST_BODY_LIMIT);
            int lastNewline = result.lastIndexOf('\n');
            if (lastNewline > POST_BODY_LIMIT - 200) {
                result = result.substring(0, lastNewline);
            }
        }

        log.debug("buildLinkedInBody() | return=length:{}", result.length());
        return result;
    }

    private String buildLinkedInComment(List<NewsArticle> articles) {
        log.debug("buildLinkedInComment() | articles={}", articles.size());

        StringBuilder sb = new StringBuilder();
        sb.append("Sources:\n\n");
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a = articles.get(i);
            String url = a.url() != null ? a.url().toString() : "";
            String toneEmoji = toneClassifier.toneEmoji(a);
            String entry = (i + 1) + ". " + toneEmoji + cleanText(a.title()) + "\n"
                    + (url.isBlank() ? "" : url + "\n")
                    + "\n";
            if (sb.length() + entry.length() > LINKS_BLOCK_LIMIT) {
                sb.append("Full source list: ").append(SITE_URL).append("\n");
                break;
            }
            sb.append(entry);
        }

        sb.append("\n").append(toneClassifier.linkedInHashtags(articles));
        sb.append(" #WeeklyRoundup");

        String result = sb.toString().trim();
        log.debug("buildLinkedInComment() | return=length:{}", result.length());
        return result;
    }

    private String buildSubstackArticle(List<NewsArticle> articles, String dateLabel,
                                        String legalSummary, String competitorSummary) {
        log.debug("buildSubstackArticle() | articles={}", articles.size());

        StringBuilder sb = new StringBuilder();
        sb.append("# AI in Healthcare — Weekly Intel Roundup\n");
        sb.append("### ").append(dateLabel).append("\n\n");

        sb.append("*AI did what to whom. When, where, and why.*\n\n");

        sb.append("---\n\n");

        sb.append("## This Week at a Glance\n\n");
        sb.append("**Legal & Regulatory:** ").append(legalSummary).append("\n\n");
        sb.append("**Competitor Watch:** ").append(competitorSummary).append("\n\n");

        sb.append("---\n\n");

        // Legal articles section
        sb.append("## Legal & Regulatory\n\n");
        int legalNum = 0;
        for (NewsArticle a : articles) {
            if ("LEGAL".equalsIgnoreCase(a.sourceTier()) || hasLegalKeywords(a)) {
                legalNum++;
                appendSubstackEntry(sb, a, legalNum);
            }
        }
        if (legalNum == 0) {
            sb.append("No significant legal developments this week.\n\n");
        }

        sb.append("---\n\n");

        // Competitor articles section
        sb.append("## Competitor Watch\n\n");
        int compNum = 0;
        for (NewsArticle a : articles) {
            if ("COMPETITOR".equalsIgnoreCase(a.sourceTier()) && !hasLegalKeywords(a)) {
                compNum++;
                appendSubstackEntry(sb, a, compNum);
            }
        }
        if (compNum == 0) {
            sb.append("No major competitor moves this week.\n\n");
        }

        sb.append("---\n\n");

        sb.append("## Sources\n\n");
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a = articles.get(i);
            String url = a.url() != null ? a.url().toString() : "";
            sb.append(i + 1).append(". [").append(cleanText(a.title())).append("](").append(url).append(")\n");
        }

        sb.append("\n---\n\n");
        sb.append("*This roundup is published every Saturday by [Big Sky Labs](").append(SITE_URL).append("). ");
        sb.append("Subscribe to get the full daily intelligence feed delivered to your inbox.*\n");

        String result = sb.toString();
        log.debug("buildSubstackArticle() | return=length:{}", result.length());
        return result;
    }

    private void appendSubstackEntry(StringBuilder sb, NewsArticle a, int num) {
        String toneEmoji = toneClassifier.toneEmoji(a);
        sb.append("### ").append(num).append(". ").append(toneEmoji).append(cleanText(a.title())).append("\n\n");

        String snippet = extractSnippet(a.bodyText());
        if (!snippet.isBlank()) {
            sb.append(snippet).append("\n\n");
        }

        if (a.sourceName() != null && !a.sourceName().isBlank()) {
            sb.append("*Source: ").append(a.sourceName()).append("*");
        }
        if (a.topic() != null && !a.topic().isBlank()) {
            sb.append(" | *Topic: ").append(a.topic()).append("*");
        }
        sb.append("\n\n");
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", " ").trim();
    }

    private String buildSearchText(NewsArticle a) {
        StringBuilder sb = new StringBuilder();
        if (a.title() != null)    { sb.append(a.title()).append(" "); }
        if (a.bodyText() != null) { sb.append(a.bodyText()); }
        return sb.toString().toLowerCase();
    }

    private String extractSnippet(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return "";
        }
        String cleaned = cleanText(bodyText);
        if (cleaned.length() <= SNIPPET_MAX_CHARS) {
            return cleaned;
        }
        String truncated = cleaned.substring(0, SNIPPET_MAX_CHARS);
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > 0) {
            truncated = truncated.substring(0, lastSpace);
        }
        return truncated + "...";
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;",  " ")
                .replace("&amp;",   "&")
                .replace("&lt;",    "<")
                .replace("&gt;",    ">")
                .replace("&quot;",  "\"")
                .replace("&apos;",  "'")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&hellip;", "…")
                .replace("&ldquo;", "“")
                .replace("&rdquo;", "”")
                .replace("&lsquo;", "‘")
                .replace("&rsquo;", "’")
                .replaceAll("&#\\d+;", " ")
                .replaceAll("&[a-zA-Z]{2,8};", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
