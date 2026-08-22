package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.web.util.ArticleToneClassifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thymeleaf controller that generates a ready-to-copy Facebook post from the
 * day's top-weighted AI healthcare articles.
 *
 * <p>Serves {@code GET /dashboard/facebook}. Same article pipeline as
 * {@link LinkedInPostController}: last 24 hours of articles via
 * {@link ArticleIngestionPort}, filtered, sorted by topic priority then
 * source weight, deduplicated by title, capped at 5.
 *
 * <p>Facebook-specific formatting (vs LinkedIn):
 * <ul>
 *   <li><strong>Post body ≤ 390 chars</strong> — numbered list of all selected
 *       articles (title only, no snippet). All 5 items appear in both the post
 *       body and the comment so the counts always match.</li>
 *   <li><strong>Links are OK in the post body</strong> — Facebook does not
 *       suppress reach for external links in posts. The SITE_URL is included
 *       in the post body itself.</li>
 *   <li><strong>Comment block</strong> — same numbered list with full snippets
 *       and source URLs. Facebook comments allow ~8,000 chars.</li>
 * </ul>
 *
 * <p>No LLM calls are made; classification uses the same keyword matching as
 * the LinkedIn generator.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-22
 * @updated 2026-08-23
 * @see ArticleToneClassifier
 */
@Slf4j
@Controller
public class FacebookDailyPostController {

    private static final int MAX_ARTICLES    = 5;
    private static final int ITEM_TITLE_MAX  = 52;  // "N. ⚠️ " = 5 chars, leaving ~52 for title
    private static final int POST_BODY_LIMIT = 390;
    private static final int COMMENT_LIMIT     = 7900;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final String SITE_URL = "https://bigskylabs.ai";
    private static final String APP_URL  = "https://app.bigskylabs.ai";

    // Topic priority — higher = appears first
    private static final int PRIORITY_LEGAL         = 4;
    private static final int PRIORITY_MARKETPLACE   = 3;
    private static final int PRIORITY_POLICY        = 2;
    private static final int PRIORITY_CONTRADICTION = 1;
    private static final int PRIORITY_GENERAL       = 0;

    private static final String[] LEGAL_KEYWORDS = {
        "lawsuit", "litigation", "court", "class action", "settlement",
        "ftc", "antitrust", "doj", "attorney general", "sec filing",
        "hipaa violation", "patent infringement", "copyright infringement",
        "criminal charges", "indictment", "legal challenge", "legal action",
        "injunction", "verdict", "subpoena", "regulatory fine", "penalty"
    };

    private static final String[] MARKETPLACE_KEYWORDS = {
        "acqui", "merger", "acquired", "acquisition", "funding round",
        "raised", "series a", "series b", "series c", "ipo",
        "private equity", "venture capital", "joint venture",
        "partnership", "deal", "billion", "investment", "buyout",
        "divest", "spin off", "strategic alliance", "m&a"
    };

    private static final String[] POLICY_KEYWORDS = {
        "fda clears", "fda approves", "fda guidance", "fda draft",
        "cms rule", "cms proposes", "cms finalizes",
        "congress", "senate", "legislation", "signed into law",
        "executive order", "white house", "hhs", "onc",
        "proposed rule", "final rule", "rulemaking", "mandate",
        "government policy", "federal policy", "state law",
        "510(k)", "de novo", "pma approval"
    };

    private static final String[] CONTRADICTION_KEYWORDS = {
        "contradicts", "reverses", "disputes", "challenges earlier",
        "conflicts with", "overturns", "refutes", "debunks",
        "prior study", "previous study", "study reversed",
        "update to earlier", "correction to", "retraction",
        "new evidence contradicts", "researchers challenge"
    };

    private final ArticleIngestionPort  articleIngestionPort;
    private final ArticleToneClassifier toneClassifier;

    public FacebookDailyPostController(ArticleIngestionPort articleIngestionPort,
                                       ArticleToneClassifier toneClassifier) {
        log.debug("FacebookDailyPostController() | articleIngestionPort={}", articleIngestionPort);
        this.articleIngestionPort = articleIngestionPort;
        this.toneClassifier       = toneClassifier;
    }

    /**
     * Renders the Facebook daily post generator page.
     *
     * @param model Thymeleaf model
     * @return the "facebook-daily-post" view name
     */
    @GetMapping("/dashboard/facebook")
    public String facebookDailyPost(Model model) {
        log.debug("facebookDailyPost() | entry");

        List<NewsArticle> raw    = articleIngestionPort.fetchRecentArticles(1);
        List<NewsArticle> usable = filterUsable(raw);
        List<NewsArticle> sorted = sortByPriorityThenWeight(usable);
        List<NewsArticle> deduped = deduplicateByTitle(sorted);
        List<NewsArticle> top    = limitList(deduped, MAX_ARTICLES);

        String dateLabel    = DATE_FMT.format(LocalDate.now(ZoneId.of("America/Chicago")));
        String postBody     = buildPostBody(top, dateLabel);
        String commentBlock = buildCommentBlock(top);

        model.addAttribute("postBody",          postBody);
        model.addAttribute("commentBlock",      commentBlock);
        model.addAttribute("articleCount",      top.size());
        model.addAttribute("dateLabel",         dateLabel);
        model.addAttribute("postBodyLength",    postBody.length());
        model.addAttribute("commentBlockLength", commentBlock.length());
        model.addAttribute("articles",          top);

        log.debug("facebookDailyPost() | return=facebook-daily-post, articles={}, postBodyLength={}",
                top.size(), postBody.length());
        return "facebook-daily-post";
    }

    // ------------------------------------------------------------------
    // Post body — lead story + bullet headlines, ≤ 390 chars
    // ------------------------------------------------------------------

    private String buildPostBody(List<NewsArticle> articles, String dateLabel) {
        log.debug("buildPostBody() | articles={}, dateLabel={}", articles.size(), dateLabel);

        StringBuilder sb = new StringBuilder();
        sb.append("AI in Healthcare — ").append(dateLabel).append("\n\n");

        if (articles.isEmpty()) {
            sb.append("No new articles in the last 24 hours.\n\n");
            sb.append(SITE_URL);
            String empty = sb.toString();
            log.debug("buildPostBody() | return=length:{}", empty.length());
            return empty;
        }

        // Numbered list — all items, tone emoji + title (no snippet).
        // Budget: 390 - header(~37) - footer(~37) = ~316 for up to 5 items at ~63 chars each.
        String footer = "\nSources in comment ↓\n" + SITE_URL;
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a = articles.get(i);
            String tone  = toneClassifier.classifyTone(a);
            String emoji = toneClassifier.toneEmoji(tone);
            String title = truncate(cleanText(a.title()), ITEM_TITLE_MAX);
            String line  = (i + 1) + ". " + emoji + title + "\n";
            if (sb.length() + line.length() + footer.length() > POST_BODY_LIMIT) {
                break;
            }
            sb.append(line);
        }

        sb.append(footer);

        String result = sb.toString();
        if (result.length() > POST_BODY_LIMIT) {
            result = result.substring(0, POST_BODY_LIMIT - 3) + "...";
        }

        log.debug("buildPostBody() | return=length:{}", result.length());
        return result;
    }

    // ------------------------------------------------------------------
    // Comment block — full articles + URLs (Facebook comments: ~8,000 chars)
    // ------------------------------------------------------------------

    private String buildCommentBlock(List<NewsArticle> articles) {
        log.debug("buildCommentBlock() | articles={}", articles.size());

        StringBuilder sb = new StringBuilder();
        sb.append("Sources:\n\n");

        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a = articles.get(i);
            String tone  = toneClassifier.classifyTone(a);
            String emoji = toneClassifier.toneEmoji(tone);
            String title   = cleanText(a.title());
            String snippet = extractSnippet(a.bodyText(), 160);
            String url     = a.url() != null ? a.url().toString() : "";

            StringBuilder entry = new StringBuilder();
            entry.append(emoji).append(title).append("\n");
            if (!snippet.isBlank()) {
                entry.append(snippet).append("\n");
            }
            if (!url.isBlank()) {
                entry.append(url).append("\n");
            }
            entry.append("\n");

            if (sb.length() + entry.length() > COMMENT_LIMIT) {
                break;
            }
            sb.append(entry);
        }

        sb.append("Full platform: ").append(APP_URL).append("\n\n");
        sb.append(toneClassifier.facebookHashtags(articles));

        String result = sb.toString();
        log.debug("buildCommentBlock() | return=length:{}", result.length());
        return result;
    }

    // ------------------------------------------------------------------
    // Pipeline: filter → sort → dedup → limit
    // ------------------------------------------------------------------

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
        if (a.title() != null && a.title().toLowerCase().contains("google")) {
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

    private List<NewsArticle> sortByPriorityThenWeight(List<NewsArticle> articles) {
        log.debug("sortByPriorityThenWeight() | articles={}", articles.size());
        List<NewsArticle> copy = new ArrayList<>(articles);
        for (int i = 1; i < copy.size(); i++) {
            NewsArticle key = copy.get(i);
            int keyPriority = classifyPriority(key);
            int j = i - 1;
            while (j >= 0) {
                int jPriority = classifyPriority(copy.get(j));
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
        log.debug("sortByPriorityThenWeight() | return={}", copy.size());
        return copy;
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

    // ------------------------------------------------------------------
    // Classification
    // ------------------------------------------------------------------

    private int classifyPriority(NewsArticle a) {
        String text = buildSearchText(a);
        if ("LEGAL".equalsIgnoreCase(a.sourceTier())) {
            return PRIORITY_LEGAL;
        }
        if ("REGULATORY".equalsIgnoreCase(a.sourceTier())) {
            return PRIORITY_POLICY;
        }
        if (containsAny(text, LEGAL_KEYWORDS))         { return PRIORITY_LEGAL; }
        if (containsAny(text, MARKETPLACE_KEYWORDS))   { return PRIORITY_MARKETPLACE; }
        if (containsAny(text, POLICY_KEYWORDS))        { return PRIORITY_POLICY; }
        if (containsAny(text, CONTRADICTION_KEYWORDS)) { return PRIORITY_CONTRADICTION; }
        return PRIORITY_GENERAL;
    }

    String classifyLabel(NewsArticle a) {
        int priority = classifyPriority(a);
        switch (priority) {
            case PRIORITY_LEGAL:         return "[LEGAL] ";
            case PRIORITY_MARKETPLACE:   return "[MARKETPLACE] ";
            case PRIORITY_POLICY:        return "[POLICY] ";
            case PRIORITY_CONTRADICTION: return "[CONTRADICTION] ";
            default:                     return "";
        }
    }

    private String buildSearchText(NewsArticle a) {
        StringBuilder sb = new StringBuilder();
        if (a.title() != null)    { sb.append(a.title()).append(" "); }
        if (a.bodyText() != null) { sb.append(a.bodyText()); }
        return sb.toString().toLowerCase();
    }

    private boolean containsAny(String text, String[] keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Text helpers
    // ------------------------------------------------------------------

    String extractSnippet(String bodyText, int maxChars) {
        if (bodyText == null || bodyText.isBlank()) {
            return "";
        }
        String cleaned = cleanText(bodyText);
        if (cleaned.length() <= maxChars) {
            return cleaned;
        }
        String truncated = cleaned.substring(0, maxChars);
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > 0) {
            truncated = truncated.substring(0, lastSpace);
        }
        return truncated + "…";
    }

    String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text != null ? text : "";
        }
        int cut = text.lastIndexOf(' ', maxChars - 1);
        return (cut > 0 ? text.substring(0, cut) : text.substring(0, maxChars - 1)) + "…";
    }

    String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;",   " ")
                .replace("&amp;",    "&")
                .replace("&lt;",     "<")
                .replace("&gt;",     ">")
                .replace("&quot;",   "\"")
                .replace("&apos;",   "'")
                .replace("&laquo;",  "«")
                .replace("&raquo;",  "»")
                .replace("&mdash;",  "—")
                .replace("&ndash;",  "–")
                .replace("&hellip;", "…")
                .replace("&ldquo;",  "“")
                .replace("&rdquo;",  "”")
                .replace("&lsquo;",  "‘")
                .replace("&rsquo;",  "’")
                .replaceAll("&#\\d+;", " ")
                .replaceAll("&[a-zA-Z]{2,8};", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", " ").trim();
    }
}
