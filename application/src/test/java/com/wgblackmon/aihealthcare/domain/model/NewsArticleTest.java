package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link NewsArticle} domain record.
 *
 * <p>Verifies that the compact constructor enforces all required field constraints
 * and that optional fields (bodyText, author, topicId, sourceName, sourceTier,
 * publishedAt) accept {@code null} without error.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-10
 */
class NewsArticleTest {

    private static final String  ID     = "article-001";
    private static final String  TITLE  = "AI Improves Diagnostic Accuracy";
    private static final URI     URL    = URI.create("https://example.com/article-001");
    private static final String  BODY   = "Researchers found that AI models outperform radiologists.";
    private static final String  TOPIC  = "AI diagnostics";
    private static final String  AUTHOR = "Dr. Jane Smith";
    private static final Instant NOW    = Instant.parse("2025-01-27T00:00:00Z");

    @Test
    void validArticle_constructsSuccessfully() {
        NewsArticle article = new NewsArticle(
                ID, TITLE, URL, BODY, TOPIC, AUTHOR,
                1L, "PubMed", "ACADEMIC", 0.9, NOW);

        assertThat(article.articleId()).isEqualTo(ID);
        assertThat(article.title()).isEqualTo(TITLE);
        assertThat(article.url()).isEqualTo(URL);
        assertThat(article.bodyText()).isEqualTo(BODY);
        assertThat(article.topic()).isEqualTo(TOPIC);
        assertThat(article.author()).isEqualTo(AUTHOR);
        assertThat(article.topicId()).isEqualTo(1L);
        assertThat(article.sourceName()).isEqualTo("PubMed");
        assertThat(article.sourceTier()).isEqualTo("ACADEMIC");
        assertThat(article.sourceWeight()).isEqualTo(0.9);
        assertThat(article.publishedAt()).isEqualTo(NOW);
    }

    @Test
    void nullAuthor_isAllowed() {
        NewsArticle article = new NewsArticle(
                ID, TITLE, URL, BODY, TOPIC, null,
                null, null, null, 0.5, null);

        assertThat(article.author()).isNull();
    }

    @Test
    void nullPublishedAt_isAllowed() {
        NewsArticle article = new NewsArticle(
                ID, TITLE, URL, BODY, TOPIC, null,
                null, null, null, 0.5, null);

        assertThat(article.publishedAt()).isNull();
    }

    @Test
    void blankBodyText_isAllowed() {
        NewsArticle article = new NewsArticle(
                ID, TITLE, URL, "  ", TOPIC, null,
                null, null, null, 0.5, null);

        assertThat(article.bodyText()).isEqualTo("  ");
    }

    @Test
    void nullSourceMetadata_isAllowed() {
        NewsArticle article = new NewsArticle(
                ID, TITLE, URL, BODY, TOPIC, AUTHOR,
                null, null, null, 0.5, null);

        assertThat(article.topicId()).isNull();
        assertThat(article.sourceName()).isNull();
        assertThat(article.sourceTier()).isNull();
    }

    @Test
    void blankArticleId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsArticle(
                "  ", TITLE, URL, BODY, TOPIC, null,
                null, null, null, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleId");
    }

    @Test
    void nullArticleId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsArticle(
                null, TITLE, URL, BODY, TOPIC, null,
                null, null, null, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleId");
    }

    @Test
    void blankTitle_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsArticle(
                ID, "", URL, BODY, TOPIC, null,
                null, null, null, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void nullUrl_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsArticle(
                ID, TITLE, null, BODY, TOPIC, null,
                null, null, null, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void blankTopic_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsArticle(
                ID, TITLE, URL, BODY, "", null,
                null, null, null, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }
}
