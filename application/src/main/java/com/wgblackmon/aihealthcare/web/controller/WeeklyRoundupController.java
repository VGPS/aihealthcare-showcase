package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.web.util.WeeklyRoundupSynthesizer;
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
 * Saturday weekly roundup post generator for LinkedIn, Facebook, and Substack.
 *
 * <p>Fetches the top 10 articles from the past 7 days filtered to COMPETITOR
 * and LEGAL source tiers, then uses LLM synthesis to produce an original
 * analytical narrative — not a link roundup. Output format:
 * <ul>
 *   <li><strong>LinkedIn post body</strong> — cohesive 3-4 paragraph analysis
 *       with hosted insights page URL for clickable OG preview card</li>
 *   <li><strong>LinkedIn first comment</strong> — source publication names
 *       (not URLs) + insights page link</li>
 *   <li><strong>Substack article</strong> — longer-form analysis with section
 *       headers, suitable for direct paste into the Substack editor</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-09-19
 * @updated 2026-09-19
 */
@Slf4j
@Controller
public class WeeklyRoundupController {

    private static final int MAX_ARTICLES = 10;
    private static final int LOOKBACK_DAYS = 7;
    private static final int POST_BODY_LIMIT = 2900;

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

    private final ArticleIngestionPort      articleIngestionPort;
    private final WeeklyRoundupSynthesizer  synthesizer;

    public WeeklyRoundupController(ArticleIngestionPort articleIngestionPort,
                                   WeeklyRoundupSynthesizer synthesizer) {
        log.debug("WeeklyRoundupController() | articleIngestionPort={}, synthesizer={}",
                articleIngestionPort, synthesizer);
        this.articleIngestionPort = articleIngestionPort;
        this.synthesizer         = synthesizer;
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

        String narrative;
        try {
            narrative = synthesizer.synthesizeNarrative(top, dateLabel, legalCount, competitorCount);
        } catch (Exception e) {
            log.warn("weeklyRoundup() | LLM synthesis failed, using fallback | error={}", e.getMessage());
            narrative = buildFallbackNarrative(top, legalCount, competitorCount);
        }

        String linkedinBody = buildLinkedInBody(narrative, dateLabel);
        String linkedinComment = buildLinkedInComment();
        String substackArticle = buildSubstackArticle(narrative, dateLabel);

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

    private String buildLinkedInBody(String narrative, String dateLabel) {
        log.debug("buildLinkedInBody() | narrative=length:{}, dateLabel={}", narrative.length(), dateLabel);

        StringBuilder sb = new StringBuilder();
        sb.append("AI in Healthcare — Weekly Intel Roundup\n");
        sb.append(dateLabel).append("\n\n");
        sb.append(narrative).append("\n\n");
        sb.append("#HealthcareAI #AIinHealthcare #DigitalHealth #HealthTech #WeeklyRoundup");

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

    private String buildLinkedInComment() {
        log.debug("buildLinkedInComment() | entry");

        StringBuilder sb = new StringBuilder();
        sb.append("Full analysis + interactive infographic:\n");
        sb.append(SITE_URL).append("/insights/\n\n");
        sb.append("We track 57+ sources and 318 companies daily.\n");
        sb.append("Subscribe for the full intelligence feed: ").append(SITE_URL).append("\n\n");
        sb.append("#HealthcareAI #AIinHealthcare #DigitalHealth #HealthTech #WeeklyRoundup");

        String result = sb.toString().trim();
        log.debug("buildLinkedInComment() | return=length:{}", result.length());
        return result;
    }

    private String buildSubstackArticle(String narrative, String dateLabel) {
        log.debug("buildSubstackArticle() | dateLabel={}", dateLabel);

        StringBuilder sb = new StringBuilder();
        sb.append("# AI in Healthcare — Weekly Intel Roundup\n");
        sb.append("### ").append(dateLabel).append("\n\n");

        sb.append("---\n\n");
        sb.append(narrative).append("\n\n");
        sb.append("---\n\n");
        sb.append("*This analysis is published every Saturday by [Big Sky Labs](").append(SITE_URL).append("). ");
        sb.append("We track 57+ sources and 318 companies daily. ");
        sb.append("Subscribe to get the full intelligence feed delivered to your inbox.*\n");

        String result = sb.toString();
        log.debug("buildSubstackArticle() | return=length:{}", result.length());
        return result;
    }

    private String buildFallbackNarrative(List<NewsArticle> articles,
                                         int legalCount, int competitorCount) {
        log.debug("buildFallbackNarrative() | articles={}", articles.size());
        StringBuilder sb = new StringBuilder();
        sb.append("This week brought ").append(articles.size()).append(" significant developments ");
        sb.append("across AI healthcare");
        if (legalCount > 0 && competitorCount > 0) {
            sb.append(" — ").append(legalCount).append(" legal/regulatory actions and ");
            sb.append(competitorCount).append(" competitor moves");
        }
        sb.append(". ");
        for (int i = 0; i < Math.min(3, articles.size()); i++) {
            NewsArticle a = articles.get(i);
            if (a.title() != null) {
                sb.append(a.title()).append(". ");
            }
        }
        sb.append("Full analysis available at app.bigskylabs.ai.");
        String result = sb.toString();
        log.debug("buildFallbackNarrative() | return=length:{}", result.length());
        return result;
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

}
