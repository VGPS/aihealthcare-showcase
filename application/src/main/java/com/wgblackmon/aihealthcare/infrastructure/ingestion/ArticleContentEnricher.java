package com.wgblackmon.aihealthcare.infrastructure.ingestion;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Enriches articles that have no meaningful body text by following their URL
 * and scraping the page content via jsoup.
 *
 * <p>Many RSS feeds and news aggregators provide only a title and link with
 * no body text (or a trivial one-line description).  This enricher bridges
 * that gap: for each article whose {@code bodyText} is null, blank, or
 * shorter than {@link #MIN_USEFUL_LENGTH} characters, it fetches the linked
 * page, extracts the main content area using the same semantic CSS selectors
 * as {@link com.wgblackmon.aihealthcare.infrastructure.ingestion.web.WebPageHarvester}
 * ({@code main, article, [role=main]} with {@code <body>} fallback), and
 * returns a new {@link NewsArticle} with the enriched body text.
 *
 * <p>Articles that already have sufficient body text are passed through
 * unchanged.  Fetch failures are logged and the article is returned as-is
 * (the downstream filter can then decide whether to keep or drop it).
 *
 * <p>Body text is truncated to {@link #MAX_BODY_LENGTH} characters to
 * prevent excessively large documents from consuming storage or context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-25
 * @updated 2026-09-05
 */
@Slf4j
@Component
public class ArticleContentEnricher {

    static final int CONNECT_TIMEOUT_MS = 15_000;
    static final int MAX_BODY_LENGTH = 2_000;
    static final int MIN_USEFUL_LENGTH = 50;

    /**
     * Enriches a list of articles by fetching page content for those with
     * empty or insufficient body text.
     *
     * @param articles The articles to enrich (never null).
     * @return A new list with enriched articles; same size as input.
     */
    public List<NewsArticle> enrich(List<NewsArticle> articles) {
        log.debug("enrich() | articleCount={}", articles.size());

        List<NewsArticle> result = new ArrayList<>();
        int enriched = 0;
        int skipped = 0;

        for (NewsArticle article : articles) {
            if (hasUsefulBody(article)) {
                result.add(article);
                skipped++;
            } else {
                NewsArticle enrichedArticle = enrichFromUrl(article);
                result.add(enrichedArticle);
                String originalBody = article.bodyText() != null ? article.bodyText() : "";
                String newBody = enrichedArticle.bodyText() != null ? enrichedArticle.bodyText() : "";
                if (!originalBody.equals(newBody)) {
                    enriched++;
                }
            }
        }

        log.info("enrich() | {} articles enriched from URL, {} already had content",
                 enriched, skipped);
        log.debug("enrich() | return={} articles", result.size());
        return result;
    }

    /**
     * Always returns {@code false} so that every article gets enriched with
     * full page content from its URL.  Previously gated by a 50-char
     * minimum threshold, but now all articles are enriched to capture
     * complete article text for the vector database archive.
     *
     * @param article The article to check.
     * @return always {@code false} — all articles are enriched
     */
    public boolean hasUsefulBody(NewsArticle article) {
        log.debug("hasUsefulBody() | articleId={}", article.articleId());

        boolean result = false;

        log.debug("hasUsefulBody() | return={}", result);
        return result;
    }

    /**
     * Attempts to fetch and extract content from the article's URL.
     *
     * <p>Returns a new {@link NewsArticle} with the enriched body text,
     * or the original article unchanged if the fetch fails or yields no content.
     *
     * @param article The article to enrich.
     * @return A new article with enriched body text, or the original on failure.
     */
    private NewsArticle enrichFromUrl(NewsArticle article) {
        log.debug("enrichFromUrl() | articleId={}, url={}", article.articleId(), article.url());

        if (article.url() == null) {
            log.debug("enrichFromUrl() | no URL — returning original");
            log.debug("enrichFromUrl() | return={}", article.articleId());
            return article;
        }

        String html = fetchPageHtml(article.url().toString());
        if (html.isEmpty()) {
            log.debug("enrichFromUrl() | fetch returned empty — returning original");
            log.debug("enrichFromUrl() | return={}", article.articleId());
            return article;
        }

        Document doc = Jsoup.parse(html);
        String content = extractMainContent(doc);

        if (content.isBlank()) {
            log.debug("enrichFromUrl() | extracted content is blank — returning original");
            log.debug("enrichFromUrl() | return={}", article.articleId());
            return article;
        }

        String truncated = ExcerptTruncator.truncate(content, MAX_BODY_LENGTH);

        log.info("enrichFromUrl() | enriched '{}' with {} chars from {}",
                 article.title(), truncated.length(), article.url());

        NewsArticle result = new NewsArticle(
                article.articleId(),
                article.title(),
                article.url(),
                truncated,
                article.topic(),
                article.author(),
                article.topicId(),
                article.sourceName(),
                article.sourceTier(),
                article.sourceWeight(),
                article.publishedAt()
        );

        log.debug("enrichFromUrl() | return={}", result.articleId());
        return result;
    }

    /**
     * Fetches page HTML via jsoup with a configured timeout.
     *
     * <p>Package-private to allow spy-based overriding in unit tests.
     *
     * @param url the page URL to fetch
     * @return raw HTML string, or empty string on failure
     */
    public String fetchPageHtml(String url) {
        log.debug("fetchPageHtml() | url={}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(CONNECT_TIMEOUT_MS)
                    .userAgent("AIHealthcare-Enricher/1.0")
                    .get();
            String html = doc.html();
            log.debug("fetchPageHtml() | return={} chars", html.length());
            return html;
        } catch (Exception ex) {
            log.warn("fetchPageHtml() | failed to fetch {}: {}", url, ex.getMessage());
            log.debug("fetchPageHtml() | return=empty");
            return "";
        }
    }

    /**
     * Extracts the main content area from the document, preferring semantic
     * containers ({@code main}, {@code article}, {@code [role=main]}) and
     * falling back to the full {@code <body>} text.
     *
     * <p>Block-level HTML elements ({@code p}, {@code h1-h6}, {@code div},
     * {@code section}, {@code li}, {@code blockquote}) are converted to double
     * newline separators before stripping tags, preserving paragraph structure
     * in the returned plain text.  This prevents all content from collapsing
     * into a single run-on sentence (a common problem with scraped web pages).
     *
     * <p>Package-private to allow direct testing.
     *
     * @param doc the parsed HTML document
     * @return structured plain text with paragraph breaks preserved, or empty string
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
     * Converts an HTML element's content to structured plain text.
     *
     * <p>Block-level opening tags are replaced with {@code \n\n} paragraph
     * separators.  {@code <br>} tags become single {@code \n}.  All remaining
     * HTML tags are stripped and HTML entities are decoded.  Runs of three or
     * more consecutive newlines are collapsed to two.
     *
     * @param root the root HTML element to convert
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

        // Decode HTML entities (e.g. &amp; &lt; &#8217;)
        html = Parser.unescapeEntities(html, false);

        // Normalize each line: collapse internal whitespace, trim
        String[] lines = html.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String normalized = line.replaceAll("[ \\t]+", " ").trim();
            sb.append(normalized).append("\n");
        }

        // Collapse 3+ consecutive newlines to exactly 2, then trim edges
        String result = sb.toString().replaceAll("\n{3,}", "\n\n").trim();

        log.debug("extractTextPreservingStructure() | return={} chars", result.length());
        return result;
    }
}
