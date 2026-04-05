package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link NewsArticle} domain record.
 *
 * <p>Verifies that the compact constructor enforces all field constraints and
 * that a fully valid instance is constructed without error.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
class NewsArticleTest {

    private static final String ID     = "article-001";
    private static final String TITLE  = "AI Improves Diagnostic Accuracy";
    private static final URI    URL    = URI.create("https://example.com/article-001");
    private static final String BODY   = "Researchers found that AI models outperform radiologists.";
    private static final String TOPIC  = "AI diagnostics";
    private static final String AUTHOR = "Dr. Jane Smith";

    @Test
    void validArticle_constructsSuccessfully() {
        NewsArticle article = new NewsArticle(ID, TITLE, URL, BODY, TOPIC, AUTHOR, LocalDate.of(2025, 1, 27));

        assertThat(article.articleId()).isEqualTo(ID);
        assertThat(article.title()).isEqualTo(TITLE);
        assertThat(article.url()).isEqualTo(URL);
        assertThat(article.bodyText()).isEqualTo(BODY);
        assertThat(article.topic()).isEqualTo(TOPIC);
        assertThat(article.author()).isEqualTo(AUTHOR);
        assertThat(article.publishedDate()).isEqualTo(LocalDate.of(2025, 1, 27));
    }

    @Test
    void nullAuthor_isAllowed() {
        NewsArticle article = new NewsArticle(ID, TITLE, URL, BODY, TOPIC, null, null);

        assertThat(article.author()).isNull();
    }

    @Test
    void nullPublishedDate_isAllowed() {
        NewsArticle article = new NewsArticle(ID, TITLE, URL, BODY, TOPIC, null, null);

        assertThat(article.publishedDate()).isNull();
    }

    @Test
    void blankArticleId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsArticle("  ", TITLE, URL, BODY, TOPIC, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleId");
    }

    @Test
    void nullArticleId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsArticle(null, TITLE, URL, BODY, TOPIC, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleId");
    }

    @Test
    void blankTitle_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsArticle(ID, "", URL, BODY, TOPIC, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void nullUrl_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsArticle(ID, TITLE, null, BODY, TOPIC, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void blankBodyText_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsArticle(ID, TITLE, URL, "  ", TOPIC, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bodyText");
    }

    @Test
    void blankTopic_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsArticle(ID, TITLE, URL, BODY, "", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }
}
