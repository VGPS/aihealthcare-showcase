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
 * articles via {@link ArticleIngestionPort}, sorting them by source weight
 * descending, capping at 5, and building two copy-ready text blocks:
 * <ul>
 *   <li><strong>Post body</strong> — 3,000-character LinkedIn post with article
 *       titles and one-line snippets. Links are intentionally omitted from the
 *       post body to avoid LinkedIn's reach-suppression for external links.</li>
 *   <li><strong>Links block</strong> — numbered source list intended to be
 *       pasted as the first comment after publishing.</li>
 * </ul>
 *
 * <p>No LLM calls are made; the post is assembled from harvested article
 * metadata only.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-08-21
 * @updated 2026-08-21
 */
@Slf4j
@Controller
public class LinkedInPostController {

    private static final int MAX_ARTICLES = 5;
    private static final int SNIPPET_MAX_CHARS = 220;
    private static final int POST_BODY_LIMIT = 2900;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final String SITE_URL = "https://app.bigskylabs.ai";

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
        List<NewsArticle> sorted = sortByWeightDesc(usable);
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

        log.debug("linkedInPost() | return=linkedin-post, articles={}, postBodyLength={}",
                top.size(), postBody.length());
        return "linkedin-post";
    }

    /**
     * Drops articles that have no usable headline or content:
     * (1) bodyText starts with "NFE/" — malformed Google News RSS entries where
     *     the body contains a browser UA string instead of article text.
     * (2) Title (normalized) equals the topic field (normalized) — the feed name
     *     was used as the headline, meaning no real article title was captured.
     * (3) Title ends with "- Google News" — the raw Google News feed label leaked
     *     into the title field.
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

    private List<NewsArticle> sortByWeightDesc(List<NewsArticle> articles) {
        log.debug("sortByWeightDesc() | articles={}", articles.size());
        List<NewsArticle> copy = new ArrayList<>(articles);

        // Insertion sort by sourceWeight descending
        for (int i = 1; i < copy.size(); i++) {
            NewsArticle key = copy.get(i);
            int j = i - 1;
            while (j >= 0 && copy.get(j).sourceWeight() < key.sourceWeight()) {
                copy.set(j + 1, copy.get(j));
                j--;
            }
            copy.set(j + 1, key);
        }

        log.debug("sortByWeightDesc() | return={}", copy.size());
        return copy;
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
     * Input must already be sorted by weight descending — the first occurrence wins.
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
                sb.append(i + 1).append(". **").append(a.title()).append("**\n");
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
            sb.append(i + 1).append(". ").append(a.title()).append("\n");
            if (!url.isBlank()) {
                sb.append("   ").append(url).append("\n");
            }
            sb.append("\n");
        }

        String result = sb.toString().trim();
        log.debug("buildLinksBlock() | return=length:{}", result.length());
        return result;
    }

    private String extractSnippet(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return "";
        }
        // Strip HTML tags
        String cleaned = bodyText.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= SNIPPET_MAX_CHARS) {
            return cleaned;
        }
        // Truncate at word boundary
        String truncated = cleaned.substring(0, SNIPPET_MAX_CHARS);
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > 0) {
            truncated = truncated.substring(0, lastSpace);
        }
        return truncated + "…";
    }
}
