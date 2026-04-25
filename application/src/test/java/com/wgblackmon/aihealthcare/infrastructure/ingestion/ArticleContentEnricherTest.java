package com.wgblackmon.aihealthcare.infrastructure.ingestion;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Unit tests for {@link ArticleContentEnricher}.
 *
 * <p>Uses a spy on the enricher to override {@code fetchPageHtml()} so that
 * tests never make real HTTP calls.  Verifies enrichment logic, content
 * extraction, body-text threshold, and truncation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-25
 * @updated 2026-04-25
 */
class ArticleContentEnricherTest {

    private ArticleContentEnricher enricher;

    private static NewsArticle article(String id, String body) {
        return new NewsArticle(
                id,
                "Title " + id,
                URI.create("https://example.com/" + id),
                body,
                "AI Healthcare",
                null, null, "TestSource", null,
                0.5,
                null
        );
    }

    @BeforeEach
    void setUp() {
        enricher = spy(new ArticleContentEnricher());
    }

    // -------------------------------------------------------------------------
    // hasUsefulBody()
    // -------------------------------------------------------------------------

    @Test
    void hasUsefulBody_longEnoughText_returnsTrue() {
        String body = "A".repeat(ArticleContentEnricher.MIN_USEFUL_LENGTH);
        NewsArticle a = article("a-001", body);

        assertThat(enricher.hasUsefulBody(a)).isTrue();
    }

    @Test
    void hasUsefulBody_tooShortText_returnsFalse() {
        NewsArticle a = article("a-001", "Short");

        assertThat(enricher.hasUsefulBody(a)).isFalse();
    }

    @Test
    void hasUsefulBody_nullBody_returnsFalse() {
        NewsArticle a = new NewsArticle(
                "a-001", "Title", URI.create("https://example.com"), null,
                "AI", null, null, null, null, 0.5, null);

        assertThat(enricher.hasUsefulBody(a)).isFalse();
    }

    @Test
    void hasUsefulBody_blankBody_returnsFalse() {
        NewsArticle a = article("a-001", "   ");

        assertThat(enricher.hasUsefulBody(a)).isFalse();
    }

    // -------------------------------------------------------------------------
    // enrich() — articles with sufficient body text are passed through
    // -------------------------------------------------------------------------

    @Test
    void enrich_articleWithUsefulBody_passedThrough() {
        String longBody = "This is a detailed article about AI in healthcare with enough content. " +
                          "It discusses various applications and findings.";
        NewsArticle a = article("a-001", longBody);

        List<NewsArticle> result = enricher.enrich(List.of(a));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bodyText()).isEqualTo(longBody);
    }

    // -------------------------------------------------------------------------
    // enrich() — articles with empty body are enriched from URL
    // -------------------------------------------------------------------------

    @Test
    void enrich_emptyBody_fetchesContentFromUrl() {
        NewsArticle a = article("a-001", "");
        doReturn("<html><head><title>Page</title></head>" +
                 "<body><main>This is substantial page content that was scraped from the article URL for enrichment purposes.</main></body></html>")
                .when(enricher).fetchPageHtml("https://example.com/a-001");

        List<NewsArticle> result = enricher.enrich(List.of(a));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bodyText()).contains("substantial page content");
    }

    @Test
    void enrich_nullBody_fetchesContentFromUrl() {
        NewsArticle a = new NewsArticle(
                "a-001", "Title", URI.create("https://example.com/a-001"), null,
                "AI", null, null, "Src", null, 0.5, null);
        doReturn("<html><body><article>Rich content from the page that should be used to enrich the article body text.</article></body></html>")
                .when(enricher).fetchPageHtml("https://example.com/a-001");

        List<NewsArticle> result = enricher.enrich(List.of(a));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bodyText()).contains("Rich content from the page");
    }

    @Test
    void enrich_fetchFails_returnsOriginalArticle() {
        NewsArticle a = article("a-001", "");
        doReturn("").when(enricher).fetchPageHtml("https://example.com/a-001");

        List<NewsArticle> result = enricher.enrich(List.of(a));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bodyText()).isEmpty();
    }

    @Test
    void enrich_preservesAllFieldsExceptBody() {
        NewsArticle a = article("a-001", "");
        doReturn("<html><body><main>Enriched content that is long enough to be considered useful for the article.</main></body></html>")
                .when(enricher).fetchPageHtml("https://example.com/a-001");

        List<NewsArticle> result = enricher.enrich(List.of(a));

        NewsArticle enriched = result.get(0);
        assertThat(enriched.articleId()).isEqualTo("a-001");
        assertThat(enriched.title()).isEqualTo("Title a-001");
        assertThat(enriched.url()).isEqualTo(URI.create("https://example.com/a-001"));
        assertThat(enriched.topic()).isEqualTo("AI Healthcare");
        assertThat(enriched.sourceName()).isEqualTo("TestSource");
        assertThat(enriched.sourceWeight()).isEqualTo(0.5);
    }

    // -------------------------------------------------------------------------
    // enrich() — truncation
    // -------------------------------------------------------------------------

    @Test
    void enrich_longContent_truncatedToMaxLength() {
        NewsArticle a = article("a-001", "");
        String longContent = "X".repeat(ArticleContentEnricher.MAX_BODY_LENGTH + 500);
        doReturn("<html><body><main>" + longContent + "</main></body></html>")
                .when(enricher).fetchPageHtml("https://example.com/a-001");

        List<NewsArticle> result = enricher.enrich(List.of(a));

        assertThat(result.get(0).bodyText().length()).isEqualTo(ArticleContentEnricher.MAX_BODY_LENGTH);
    }

    // -------------------------------------------------------------------------
    // extractMainContent()
    // -------------------------------------------------------------------------

    @Test
    void extractMainContent_prefersMainElement() {
        Document doc = Jsoup.parse(
                "<html><body><nav>Nav</nav><main>Main content here</main><footer>Foot</footer></body></html>");

        String content = enricher.extractMainContent(doc);

        assertThat(content).isEqualTo("Main content here");
    }

    @Test
    void extractMainContent_prefersArticleElement() {
        Document doc = Jsoup.parse(
                "<html><body><div>Sidebar</div><article>Article content here</article></body></html>");

        String content = enricher.extractMainContent(doc);

        assertThat(content).isEqualTo("Article content here");
    }

    @Test
    void extractMainContent_fallsBackToBody() {
        Document doc = Jsoup.parse(
                "<html><body><div>All body content</div></body></html>");

        String content = enricher.extractMainContent(doc);

        assertThat(content).isEqualTo("All body content");
    }
}
