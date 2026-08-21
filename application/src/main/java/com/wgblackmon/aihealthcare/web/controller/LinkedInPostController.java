package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
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
 * Thymeleaf controller that generates a ready-to-copy LinkedIn post from the
 * day's top-weighted AI healthcare articles.
 *
 * <p>Serves {@code GET /dashboard/linkedin} by fetching the last 24 hours of
 * articles via {@link ArticleIngestionPort}, classifying each article into a
 * preferred topic category (LEGAL, MARKETPLACE, POLICY, CONTRADICTION, or
 * GENERAL), sorting by category priority then source weight, deduplicating by
 * title, and capping at 5 articles.
 *
 * <p>Two copy-ready text blocks are produced:
 * <ul>
 *   <li><strong>Post body</strong> — ≤3,000-char LinkedIn post with category
 *       labels, bold titles, and expanded snippets. Links are intentionally
 *       omitted to avoid LinkedIn's reach-suppression for external links.</li>
 *   <li><strong>Links block</strong> — numbered source list for the first
 *       comment after publishing.</li>
 * </ul>
 *
 * <p>No LLM calls are made; classification uses keyword matching only.
 *
 * @author  Bill Blackmon
 * @version 1.4
 * @since   2026-08-21
 * @updated 2026-08-21
 */
@Slf4j
@Controller
public class LinkedInPostController {

    private static final int MAX_ARTICLES = 5;
    private static final int SNIPPET_MAX_CHARS = 220;
    private static final int POST_BODY_LIMIT = 2900;
    private static final int LINKS_BLOCK_LIMIT = 1200;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final String SITE_URL = "https://app.bigskylabs.ai";

    // Topic priority — higher = appears first in the post
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

    private final ArticleIngestionPort articleIngestionPort;

    public LinkedInPostController(ArticleIngestionPort articleIngestionPort) {
        log.debug("LinkedInPostController() | articleIngestionPort={}", articleIngestionPort);
        this.articleIngestionPort = articleIngestionPort;
    }

    /**
     * Renders the LinkedIn post generator page.
     *
     * @param model Thymeleaf model
     * @return the "linkedin-post" view name
     */
    @GetMapping("/dashboard/linkedin")
    public String linkedInPost(Model model) {
        log.debug("linkedInPost() | entry");

        List<NewsArticle> raw = articleIngestionPort.fetchRecentArticles(1);
        List<NewsArticle> usable = filterUsable(raw);
        List<NewsArticle> sorted = sortByPriorityThenWeight(usable);
        List<NewsArticle> deduped = deduplicateByTitle(sorted);
        List<NewsArticle> top = limitList(deduped, MAX_ARTICLES);

        String dateLabel = DATE_FMT.format(LocalDate.now(ZoneId.of("America/Chicago")));
        String postBody = buildPostBody(top, dateLabel);
        String linksBlock = buildLinksBlock(top);

        model.addAttribute("postBody", postBody);
        model.addAttribute("linksBlock", linksBlock);
        model.addAttribute("articleCount", top.size());
        model.addAttribute("dateLabel", dateLabel);
        model.addAttribute("postBodyLength", postBody.length());
        model.addAttribute("linksBlockLength", linksBlock.length());

        log.debug("linkedInPost() | return=linkedin-post, articles={}, postBodyLength={}",
                top.size(), postBody.length());
        return "linkedin-post";
    }

    /**
     * Drops articles that should not appear in a LinkedIn post:
     * (1) sourceWeight below 0.5 (below 5/10) — low-quality or unranked sources.
     * (2) Title contains "Google" — excludes Google-branded content and Google
     *     News aggregation artifacts; keeps the post independent and unique.
     * (3) bodyText starts with "NFE/" — malformed Google News RSS entries.
     * (4) Title contains "- Google News" — raw feed label leaked into headline.
     * (5) Normalized title equals normalized topic — feed name used as headline.
     */
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
        // Below the 5/10 quality threshold
        if (a.sourceWeight() < 0.5) {
            return false;
        }
        // Google-branded content or Google News aggregation artifact
        if (a.title() != null && a.title().toLowerCase().contains("google")) {
            return false;
        }
        // Malformed Google News RSS — body is a browser UA string, not article text
        if (a.bodyText() != null && a.bodyText().trim().startsWith("NFE/")) {
            return false;
        }
        // Raw Google News feed label leaked into title
        if (a.title() != null && a.title().contains("- Google News")) {
            return false;
        }
        // Title is just the topic/feed name — no real headline was captured
        String titleNorm = normalizeTitle(a.title());
        String topicNorm = normalizeTitle(a.topic());
        if (!titleNorm.isBlank() && !topicNorm.isBlank() && titleNorm.equals(topicNorm)) {
            return false;
        }
        return true;
    }

    /**
     * Sorts articles by topic priority descending, then source weight descending.
     * LEGAL (4) > MARKETPLACE (3) > POLICY (2) > CONTRADICTION (1) > GENERAL (0).
     */
    private List<NewsArticle> sortByPriorityThenWeight(List<NewsArticle> articles) {
        log.debug("sortByPriorityThenWeight() | articles={}", articles.size());
        List<NewsArticle> copy = new ArrayList<>(articles);

        // Insertion sort — stable, correct for small lists
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

    /**
     * Returns the topic priority score for a single article by matching its
     * title and body text against keyword sets in priority order.
     * Source tier "LEGAL" and "REGULATORY" are also used as signals.
     */
    private int classifyPriority(NewsArticle a) {
        String text = buildSearchText(a);

        // Explicit tier signals take precedence
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

    /**
     * Returns the display label for an article's topic category.
     * Returns an empty string for GENERAL so unlabeled articles stay clean.
     */
    private String classifyLabel(NewsArticle a) {
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

    /**
     * Removes articles with duplicate titles (case-insensitive, punctuation-stripped).
     * Input must already be sorted — the first (highest-priority) occurrence wins.
     */
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

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", " ").trim();
    }

    private String buildPostBody(List<NewsArticle> articles, String dateLabel) {
        log.debug("buildPostBody() | articles={}, dateLabel={}", articles.size(), dateLabel);

        StringBuilder sb = new StringBuilder();
        sb.append("AI in Healthcare — ").append(dateLabel).append("\n\n");

        if (articles.isEmpty()) {
            sb.append("No new articles in the last 24 hours.\n");
        } else {
            for (int i = 0; i < articles.size(); i++) {
                NewsArticle a = articles.get(i);
                String label = classifyLabel(a);
                sb.append(i + 1).append(". **").append(label).append(a.title()).append("**\n");
                String snippet = extractSnippet(a.bodyText());
                if (!snippet.isBlank()) {
                    sb.append(snippet).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("Source links in the first comment below.\n\n");
        sb.append("Follow for daily AI healthcare intelligence.\n");
        sb.append("→ Full platform: ").append(SITE_URL).append("\n\n");
        sb.append("#AIHealthcare #HealthTech #HealthcareAI #DigitalHealth #MedTech");

        String result = sb.toString();
        if (result.length() > POST_BODY_LIMIT) {
            result = result.substring(0, POST_BODY_LIMIT);
            int lastNewline = result.lastIndexOf('\n');
            if (lastNewline > POST_BODY_LIMIT - 200) {
                result = result.substring(0, lastNewline);
            }
        }

        log.debug("buildPostBody() | return=length:{}", result.length());
        return result;
    }

    private String buildLinksBlock(List<NewsArticle> articles) {
        log.debug("buildLinksBlock() | articles={}", articles.size());

        StringBuilder sb = new StringBuilder();
        sb.append("Sources:\n");
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a = articles.get(i);
            String url = a.url() != null ? a.url().toString() : "";
            String entry = (i + 1) + ". " + a.title() + "\n"
                    + (url.isBlank() ? "" : "   " + url + "\n")
                    + "\n";
            if (sb.length() + entry.length() > LINKS_BLOCK_LIMIT) {
                sb.append("Full source list: ").append(SITE_URL).append("\n");
                break;
            }
            sb.append(entry);
        }

        String result = sb.toString().trim();
        log.debug("buildLinksBlock() | return=length:{}", result.length());
        return result;
    }

    private String extractSnippet(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return "";
        }
        String cleaned = bodyText.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= SNIPPET_MAX_CHARS) {
            return cleaned;
        }
        String truncated = cleaned.substring(0, SNIPPET_MAX_CHARS);
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > 0) {
            truncated = truncated.substring(0, lastSpace);
        }
        return truncated + "…";
    }
}
