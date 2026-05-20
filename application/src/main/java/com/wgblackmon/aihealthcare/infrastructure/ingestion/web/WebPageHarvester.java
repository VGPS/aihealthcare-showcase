package com.wgblackmon.aihealthcare.infrastructure.ingestion.web;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ContentHashPort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceConfig;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Infrastructure adapter that scrapes configured competitor web pages using
 * jsoup and detects content changes via SHA-256 hashing.
 *
 * <p>Only pages configured with tier {@code COMPETITOR} in
 * {@link FeedSourceProperties} are processed.  On each harvest cycle the
 * adapter fetches the page HTML, extracts the main content area (falling
 * back to {@code <body>} if no semantic container is found), computes a
 * SHA-256 digest, and compares it against the stored hash via
 * {@link ContentHashPort}.  Changed (or first-seen) pages are returned as
 * {@link NewsArticle} records ready for persistence in the existing pipeline.
 *
 * <p>Each detected change produces a new {@link NewsArticle} whose URL
 * includes a {@code #snapshot-{epochSeconds}} fragment to bypass the
 * URL-based deduplication in
 * {@link com.wgblackmon.aihealthcare.infrastructure.persistence.ArticleStorageAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-05-20
 */
@Slf4j
@Component
public class WebPageHarvester {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int MAX_BODY_LENGTH = 10_000;

    private final List<FeedSourceConfig> competitorSources;
    private final ContentHashPort contentHashPort;

    public WebPageHarvester(FeedSourceProperties properties,
                            ContentHashPort contentHashPort) {
        log.debug("WebPageHarvester() | properties={}, contentHashPort={}",
                  properties.getClass().getSimpleName(),
                  contentHashPort.getClass().getSimpleName());
        List<FeedSourceConfig> filtered = new ArrayList<>();
        for (FeedSourceConfig config : properties.toFeedSourceConfigs()) {
            if (config.tier() == FeedSourceConfig.FeedTier.COMPETITOR) {
                filtered.add(config);
            }
        }
        this.competitorSources = List.copyOf(filtered);
        this.contentHashPort = contentHashPort;
        log.debug("WebPageHarvester() | initialized with {} competitor sources",
                  competitorSources.size());
    }

    /**
     * Scrapes all configured competitor pages and returns articles for those
     * whose content has changed since the last check.
     *
     * @return list of {@link NewsArticle} records for changed pages (never null)
     */
    public List<NewsArticle> harvestChangedPages() {
        log.debug("harvestChangedPages() | checking {} competitor pages",
                  competitorSources.size());
        List<NewsArticle> results = new ArrayList<>();

        for (FeedSourceConfig source : competitorSources) {
            try {
                NewsArticle article = harvestPage(source);
                if (article != null) {
                    results.add(article);
                }
            } catch (Exception ex) {
                log.error("harvestChangedPages() | failed to harvest '{}': {}",
                          source.name(), ex.getMessage(), ex);
            }
        }

        log.info("harvestChangedPages() | {} changed pages detected out of {} checked",
                 results.size(), competitorSources.size());
        log.debug("harvestChangedPages() | return={} articles", results.size());
        return List.copyOf(results);
    }

    /**
     * Fetches a single page, checks for content changes, and returns a
     * {@link NewsArticle} if the content has changed (or is new).
     *
     * @param source the competitor page configuration
     * @return a new article if content changed, {@code null} if unchanged
     */
    private NewsArticle harvestPage(FeedSourceConfig source) {
        log.debug("harvestPage() | source={}, url={}", source.name(), source.url());

        Document doc = Jsoup.parse(fetchPageHtml(source.url()));
        String mainContent = extractMainContent(doc);
        String newHash = sha256(mainContent);

        String storedHash = contentHashPort.getHash(source.url());
        if (newHash.equals(storedHash)) {
            log.debug("harvestPage() | no change detected for '{}'", source.name());
            log.debug("harvestPage() | return=null");
            return null;
        }

        contentHashPort.saveHash(source.url(), newHash);
        log.info("harvestPage() | change detected for '{}' (hash: {} -> {})",
                 source.name(),
                 storedHash != null ? storedHash.substring(0, 8) + "..." : "NEW",
                 newHash.substring(0, 8) + "...");

        // Keyword filter: if source declares keywords, skip article if none match page content
        if (!source.matchesKeywords(mainContent)) {
            log.debug("harvestPage() | keyword filter: no match for '{}', skipping article", source.name());
            log.debug("harvestPage() | return=null");
            return null;
        }

        String title = doc.title().isBlank() ? source.name() : doc.title();
        String bodyText = mainContent.length() > MAX_BODY_LENGTH
                ? mainContent.substring(0, MAX_BODY_LENGTH)
                : mainContent;

        long epochSeconds = Instant.now().getEpochSecond();
        URI snapshotUrl = URI.create(source.url() + "#snapshot-" + epochSeconds);

        NewsArticle article = new NewsArticle(
                UUID.randomUUID().toString(),
                title,
                snapshotUrl,
                bodyText,
                source.effectiveTopic(),
                source.name(),
                source.topicId(),
                source.name(),
                source.tier().name(),
                source.baseWeight(),
                Instant.now()
        );

        log.debug("harvestPage() | return={}", article.title());
        return article;
    }

    /**
     * Fetches page HTML via jsoup with configured timeout.
     *
     * @param url the page URL
     * @return raw HTML string
     */
    String fetchPageHtml(String url) {
        log.debug("fetchPageHtml() | url={}", url);
        Instant startTime = Instant.now();
        log.debug("fetchPageHtml() | PRE-SCRAPE  url={} startTime={}", url, startTime);
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(CONNECT_TIMEOUT_MS)
                    .userAgent("AIHealthcare-Monitor/1.0")
                    .get();
            String html = doc.html();
            Instant endTime = Instant.now();
            long elapsedMs = java.time.Duration.between(startTime, endTime).toMillis();
            String preview = html.length() > 500 ? html.substring(0, 500) + "..." : html;
            log.debug("fetchPageHtml() | POST-SCRAPE url={} endTime={} elapsedMs={} contentLength={} contentPreview={}",
                      url, endTime, elapsedMs, html.length(), preview);
            log.debug("fetchPageHtml() | return={} chars", html.length());
            return html;
        } catch (Exception ex) {
            Instant endTime = Instant.now();
            long elapsedMs = java.time.Duration.between(startTime, endTime).toMillis();
            log.error("fetchPageHtml() | POST-SCRAPE FAILED url={} endTime={} elapsedMs={} error={}",
                      url, endTime, elapsedMs, ex.getMessage());
            log.debug("fetchPageHtml() | return=empty");
            return "";
        }
    }

    /**
     * Extracts the main content area from the document, preferring semantic
     * containers ({@code main}, {@code article}, {@code [role=main]}) to
     * avoid false change detection from navigation and footer churn.
     *
     * <p>Block-level HTML elements ({@code p}, {@code h1-h6}, {@code div},
     * {@code section}, {@code li}, {@code blockquote}) are converted to
     * {@code \n\n} paragraph separators before stripping tags.  This preserves
     * paragraph structure so that competitor page content is not flattened into
     * a single run-on sentence in the HTML summary export.
     *
     * @param doc the parsed HTML document
     * @return structured plain text with {@code \n\n} paragraph breaks, or empty
     */
    String extractMainContent(Document doc) {
        log.debug("extractMainContent() | title={}", doc.title());

        Elements mainElements = doc.select("main, article, [role=main]");
        Element root;
        if (!mainElements.isEmpty()) {
            root = mainElements.first();
        } else if (doc.body() != null) {
            root = doc.body();
        } else {
            log.debug("extractMainContent() | return=empty (no body element)");
            return "";
        }

        String content = extractTextPreservingStructure(root);

        log.debug("extractMainContent() | return={} chars", content.length());
        return content;
    }

    /**
     * Converts an HTML element's content to structured plain text by replacing
     * block-level opening tags with {@code \n\n} separators, {@code <br>} with
     * {@code \n}, stripping remaining tags, and decoding HTML entities.
     *
     * @param root the root element to convert
     * @return normalized plain text with paragraph breaks as {@code \n\n}
     */
    private String extractTextPreservingStructure(Element root) {
        log.debug("extractTextPreservingStructure() | tag={}", root.tagName());

        String html = root.html();

        // Remove entire <style>, <script>, and <noscript> blocks (content + tags)
        // so that inline CSS and JavaScript do not appear as text output
        html = html.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        html = html.replaceAll("(?is)<noscript[^>]*>.*?</noscript>", "");

        // Replace block-level opening tags with paragraph separator
        html = html.replaceAll(
                "(?i)<(p|h[1-6]|div|section|article|li|blockquote|pre|header|footer|tr)[^>]*>",
                "\n\n");

        // Replace <br> variants with single newline
        html = html.replaceAll("(?i)<br[^>]*/?>", "\n");

        // Strip all remaining HTML tags
        html = html.replaceAll("<[^>]+>", "");

        // Decode HTML entities
        html = Parser.unescapeEntities(html, false);

        // Normalize each line: collapse internal whitespace, trim
        String[] lines = html.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String normalized = line.replaceAll("[ \\t]+", " ").trim();
            sb.append(normalized).append("\n");
        }

        // Collapse 3+ consecutive newlines to exactly 2, trim edges
        String result = sb.toString().replaceAll("\n{3,}", "\n\n").trim();

        log.debug("extractTextPreservingStructure() | return={} chars", result.length());
        return result;
    }

    /**
     * Computes the SHA-256 hex digest of the given text.
     *
     * @param text the text to hash
     * @return lowercase hex string (64 chars)
     */
    String sha256(String text) {
        log.debug("sha256() | text.length={}", text.length());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            String result = hex.toString();
            log.debug("sha256() | return={}", result.substring(0, 8) + "...");
            return result;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
