package com.wgblackmon.aihealthcare.infrastructure.ingestion;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
 * @updated 2026-04-25
 */
@Slf4j
@Component
public class ArticleContentEnricher {

    static final int CONNECT_TIMEOUT_MS = 15_000;
    static final int MAX_BODY_LENGTH = 10_000;
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
     * Checks whether an article has a useful body text (non-null, non-blank,
     * and at least {@link #MIN_USEFUL_LENGTH} characters).
     *
     * @param article The article to check.
     * @return {@code true} if the body text is considered useful.
     */
    public boolean hasUsefulBody(NewsArticle article) {
        log.debug("hasUsefulBody() | articleId={}", article.articleId());

        boolean result = article.bodyText() != null
                && !article.bodyText().isBlank()
                && article.bodyText().length() >= MIN_USEFUL_LENGTH;

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

        String truncated = content.length() > MAX_BODY_LENGTH
                ? content.substring(0, MAX_BODY_LENGTH)
                : content;

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
     * <p>Package-private to allow direct testing.
     *
     * @param doc the parsed HTML document
     * @return text content of the main area, or full body text as fallback
     */
    String extractMainContent(Document doc) {
        log.debug("extractMainContent() | title={}", doc.title());

        Elements mainElements = doc.select("main, article, [role=main]");
        String content;
        if (!mainElements.isEmpty()) {
            content = mainElements.first().text();
        } else {
            content = doc.body() != null ? doc.body().text() : "";
        }

        log.debug("extractMainContent() | return={} chars", content.length());
        return content;
    }
}
