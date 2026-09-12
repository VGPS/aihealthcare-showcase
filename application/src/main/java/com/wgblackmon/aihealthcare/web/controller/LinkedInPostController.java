package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DailySummaryPort;
import com.wgblackmon.aihealthcare.web.util.ArticleToneClassifier;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
 * @version 1.5
 * @since   2026-08-21
 * @updated 2026-09-12
 * @see ArticleToneClassifier
 */
@Slf4j
@Controller
public class LinkedInPostController {

    private static final int MAX_ARTICLES = 5;
    private static final int SNIPPET_MAX_CHARS = 220;
    private static final int POST_BODY_LIMIT = 2900;
    // LinkedIn comment limit is 1,250 chars; reserve ~200 for hashtags + buffer
    private static final int LINKS_BLOCK_LIMIT = 1050;

    private static final DateTimeFormatter DATE_FMT =
            DisplayFormats.LONG_DATE;
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

    private final ArticleIngestionPort  articleIngestionPort;
    private final ArticleToneClassifier toneClassifier;
    private final DailySummaryPort      dailySummaryPort;

    public LinkedInPostController(ArticleIngestionPort articleIngestionPort,
                                  ArticleToneClassifier toneClassifier,
                                  DailySummaryPort dailySummaryPort) {
        log.debug("LinkedInPostController() | articleIngestionPort={}", articleIngestionPort);
        this.articleIngestionPort = articleIngestionPort;
        this.toneClassifier       = toneClassifier;
        this.dailySummaryPort     = dailySummaryPort;
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
                NewsArticle a     = articles.get(i);
                String label      = classifyLabel(a);
                String toneEmoji  = toneClassifier.toneEmoji(a);
                sb.append(i + 1).append(". ").append(toneEmoji)
                  .append("**").append(label).append(cleanText(a.title())).append("**\n");
                String snippet = extractSnippet(a.bodyText());
                if (!snippet.isBlank()) {
                    sb.append(snippet).append("\n");
                }
                sb.append("\n\n");
            }
        }

        sb.append("Source links in the first comment below.\n\n");
        sb.append("Follow for daily AI healthcare intelligence.\n");
        sb.append("→ Full platform: ").append(SITE_URL).append("\n\n");
        sb.append("Please contact me if you would like to contribute links with relevant information from reputable sources.\n\n");
        sb.append(toneClassifier.linkedInHashtags(articles));

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
        sb.append("Sources:\n\n");
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a    = articles.get(i);
            String rawUrl    = a.url() != null ? a.url().toString() : "";
            String url       = cleanUrlForDisplay(resolveUrl(rawUrl));
            String toneEmoji = toneClassifier.toneEmoji(a);
            String entry = toneEmoji + cleanText(a.title()) + "\n"
                    + (url.isBlank() ? "" : url + "\n")
                    + "\n\n";
            if (sb.length() + entry.length() > LINKS_BLOCK_LIMIT) {
                sb.append("Full source list: ").append(SITE_URL).append("\n");
                break;
            }
            sb.append(entry);
        }

        sb.append("\n").append(toneClassifier.linkedInHashtags(articles));

        String result = sb.toString().trim();
        log.debug("buildLinksBlock() | return=length:{}", result.length());
        return result;
    }

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
            "fc", "ff", "v", "oc", "hl", "gl", "ceid"
    );

    /**
     * Follows redirects for Google News RSS URLs using Java HttpClient.
     * {@code HttpResponse.uri()} returns the final URI after all redirects.
     * Falls back to the original URL on any failure.
     */
    private String resolveUrl(String rawUrl) {
        log.debug("resolveUrl() | rawUrl={}", rawUrl);
        if (rawUrl == null || rawUrl.isBlank() || !rawUrl.contains("news.google.com")) {
            log.debug("resolveUrl() | return={}", rawUrl);
            return rawUrl;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rawUrl))
                    .GET()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            String resolved = response.uri().toString();
            log.debug("resolveUrl() | return={}", resolved);
            return (resolved == null || resolved.isBlank()) ? rawUrl : resolved;
        } catch (Exception e) {
            log.warn("resolveUrl() | resolution failed, returning raw. url={}", rawUrl);
            log.debug("resolveUrl() | return={}", rawUrl);
            return rawUrl;
        }
    }

    /**
     * Strips UTM/tracking query params and #snapshot fragments from URLs to shorten
     * them for the LinkedIn comment block.
     */
    private String cleanUrlForDisplay(String url) {
        log.debug("cleanUrlForDisplay() | url={}", url);
        if (url == null || url.isBlank()) {
            return url;
        }
        // Strip #snapshot-* fragments (not useful and waste chars)
        int hashIdx = url.indexOf('#');
        if (hashIdx > 0 && url.substring(hashIdx).startsWith("#snapshot")) {
            url = url.substring(0, hashIdx);
        }
        // Strip query string when all params are known tracking params
        int queryIdx = url.indexOf('?');
        if (queryIdx > 0) {
            String query = url.substring(queryIdx + 1);
            String[] params = query.split("&");
            boolean allTracking = true;
            for (String param : params) {
                String name = param.split("=")[0].toLowerCase();
                if (!TRACKING_PARAMS.contains(name)) {
                    allTracking = false;
                    break;
                }
            }
            if (allTracking) {
                url = url.substring(0, queryIdx);
            }
        }
        log.debug("cleanUrlForDisplay() | return={}", url);
        return url;
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
        return truncated + "…";
    }

    /**
     * Renders a LinkedIn post generator page populated from the most recent
     * NotebookLM research summary (HTML) rather than today's live article feed.
     *
     * <p>Reads the executive summary and numbered source list from
     * {@code NotebookLMDirectory/summaries/yyyy_MM_dd.html} via
     * {@link DailySummaryPort#findMostRecentSummaryDate()} →
     * {@link DailySummaryPort#getHtmlSummary(java.time.LocalDate)}.
     * Falls back gracefully when no summary file is available.
     *
     * @param model Thymeleaf model
     * @return the "linkedin-post" view name
     */
    @GetMapping("/dashboard/linkedin/research-summary")
    public String researchSummaryPost(Model model) {
        log.debug("researchSummaryPost() | entry");

        LocalDate summaryDate = dailySummaryPort.findMostRecentSummaryDate().orElse(null);

        if (summaryDate == null) {
            model.addAttribute("postBody",
                    "No research summary available. Run a research harvest first.");
            model.addAttribute("linksBlock", "");
            model.addAttribute("articleCount", 0);
            model.addAttribute("dateLabel",
                    DATE_FMT.format(LocalDate.now(ZoneId.of("America/Chicago"))));
            model.addAttribute("postBodyLength", 0);
            model.addAttribute("linksBlockLength", 0);
            model.addAttribute("pageTitle", "Research Summary — LinkedIn Post");
            log.debug("researchSummaryPost() | return=linkedin-post, no summary found");
            return "linkedin-post";
        }

        Optional<String> htmlOpt = dailySummaryPort.getHtmlSummary(summaryDate);
        if (htmlOpt.isEmpty()) {
            String dateLabel = DATE_FMT.format(summaryDate);
            model.addAttribute("postBody",
                    "Research summary file not found for " + dateLabel + ".");
            model.addAttribute("linksBlock", "");
            model.addAttribute("articleCount", 0);
            model.addAttribute("dateLabel", dateLabel);
            model.addAttribute("postBodyLength", 0);
            model.addAttribute("linksBlockLength", 0);
            model.addAttribute("pageTitle", "Research Summary — LinkedIn Post");
            log.debug("researchSummaryPost() | return=linkedin-post, html empty");
            return "linkedin-post";
        }

        Document doc = Jsoup.parse(htmlOpt.get());
        String pageHeading = doc.select("h1").text();
        String dateLabel   = DATE_FMT.format(summaryDate);

        String summaryText = "";
        Element summaryDiv = doc
                .select("div[style*=background:#f0f7ff] div[style*=font-size:0.95em]")
                .first();
        if (summaryDiv != null) {
            summaryText = summaryDiv.text();
        }

        List<String[]> articles = new ArrayList<>();
        for (Element articleDiv : doc.select("div[id^=article-]")) {
            Element link = articleDiv.select("a[href]").first();
            if (link != null) {
                articles.add(new String[]{link.text(), link.attr("href")});
            }
        }

        String postBody   = buildResearchPostBody(pageHeading, dateLabel, summaryText, articles.size());
        String linksBlock = buildResearchLinksBlock(articles, dateLabel);

        model.addAttribute("postBody", postBody);
        model.addAttribute("linksBlock", linksBlock);
        model.addAttribute("articleCount", articles.size());
        model.addAttribute("dateLabel", dateLabel + " — " + pageHeading);
        model.addAttribute("postBodyLength", postBody.length());
        model.addAttribute("linksBlockLength", linksBlock.length());
        model.addAttribute("pageTitle", "Research Summary — LinkedIn Post");

        log.debug("researchSummaryPost() | return=linkedin-post, articles={}, postBodyLength={}",
                articles.size(), postBody.length());
        return "linkedin-post";
    }

    private String buildResearchPostBody(String pageHeading, String dateLabel,
                                         String summaryText, int articleCount) {
        log.debug("buildResearchPostBody() | pageHeading={}, dateLabel={}, articleCount={}",
                pageHeading, dateLabel, articleCount);

        StringBuilder sb = new StringBuilder();
        sb.append("AI Healthcare Intelligence — ").append(pageHeading).append("\n");
        sb.append(dateLabel).append(" • ").append(articleCount).append(" sources\n\n");

        if (!summaryText.isBlank()) {
            sb.append(summaryText).append("\n\n");
        }

        sb.append("Source links in the first comment below.\n\n");
        sb.append("Follow for daily AI healthcare intelligence.\n");
        sb.append("→ Full platform: ").append(SITE_URL).append("\n\n");
        sb.append("#AIHealthcare #HealthcareAI #DigitalHealth #HealthTech #MedicalInnovation");

        String result = sb.toString();
        if (result.length() > POST_BODY_LIMIT) {
            result = result.substring(0, POST_BODY_LIMIT);
            int lastNewline = result.lastIndexOf('\n');
            if (lastNewline > POST_BODY_LIMIT - 200) {
                result = result.substring(0, lastNewline);
            }
        }

        log.debug("buildResearchPostBody() | return=length:{}", result.length());
        return result;
    }

    private String buildResearchLinksBlock(List<String[]> articles, String dateLabel) {
        log.debug("buildResearchLinksBlock() | articles={}, dateLabel={}", articles.size(), dateLabel);

        StringBuilder sb = new StringBuilder();
        sb.append("Sources (").append(dateLabel).append("):\n\n");
        for (int i = 0; i < articles.size(); i++) {
            String[] entry = articles.get(i);
            String item = (i + 1) + ". " + entry[0] + "\n" + entry[1] + "\n\n";
            if (sb.length() + item.length() > LINKS_BLOCK_LIMIT) {
                sb.append("Full source list: ").append(SITE_URL).append("\n");
                break;
            }
            sb.append(item);
        }

        sb.append("\n#AIHealthcare #HealthcareAI #DigitalHealth");

        String result = sb.toString().trim();
        log.debug("buildResearchLinksBlock() | return=length:{}", result.length());
        return result;
    }

    /**
     * Strips HTML tags, decodes common HTML entities, and collapses whitespace.
     * Handles named entities (&amp;nbsp; &amp;amp; etc.) and numeric entities (&#160; &#8217; etc.).
     */
    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("<[^>]+>", " ")          // strip HTML tags
                .replace("&nbsp;",  " ")
                .replace("&amp;",   "&")
                .replace("&lt;",    "<")
                .replace("&gt;",    ">")
                .replace("&quot;",  "\"")
                .replace("&apos;",  "'")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&hellip;", "…")
                .replace("&ldquo;", "“")
                .replace("&rdquo;", "”")
                .replace("&lsquo;", "‘")
                .replace("&rsquo;", "’")
                .replaceAll("&#\\d+;", " ")           // numeric entities → space
                .replaceAll("&[a-zA-Z]{2,8};", " ")  // any remaining named entities
                .replaceAll("\\s+", " ")
                .trim();
    }
}
