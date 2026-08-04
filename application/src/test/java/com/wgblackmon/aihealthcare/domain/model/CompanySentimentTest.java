package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CompanySentiment}, {@link ArticleSentiment},
 * and {@link SentimentLabel} domain records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
class CompanySentimentTest {

    @Test
    void validCompanySentiment_createsSuccessfully() {
        CompanySentiment s = new CompanySentiment(
                "tempus-ai", "Tempus AI", SentimentLabel.POSITIVE, 0.6,
                10, 7, 1, 1, 1, "Mostly positive coverage.",
                List.of(), Instant.now());

        assertThat(s.companySlug()).isEqualTo("tempus-ai");
        assertThat(s.overallSentiment()).isEqualTo(SentimentLabel.POSITIVE);
        assertThat(s.sentimentScore()).isEqualTo(0.6);
    }

    @Test
    void companySentiment_nullSlug_throws() {
        assertThatThrownBy(() -> new CompanySentiment(
                null, "Tempus", SentimentLabel.NEUTRAL, 0, 0, 0, 0, 0, 0,
                "Summary", List.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companySlug");
    }

    @Test
    void companySentiment_blankName_throws() {
        assertThatThrownBy(() -> new CompanySentiment(
                "slug", "  ", SentimentLabel.NEUTRAL, 0, 0, 0, 0, 0, 0,
                "Summary", List.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyName");
    }

    @Test
    void companySentiment_nullSentiment_throws() {
        assertThatThrownBy(() -> new CompanySentiment(
                "slug", "Name", null, 0, 0, 0, 0, 0, 0,
                "Summary", List.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overallSentiment");
    }

    @Test
    void companySentiment_nullAnalyzedAt_throws() {
        assertThatThrownBy(() -> new CompanySentiment(
                "slug", "Name", SentimentLabel.NEUTRAL, 0, 0, 0, 0, 0, 0,
                "Summary", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("analyzedAt");
    }

    @Test
    void companySentiment_nullList_defaultsToEmpty() {
        CompanySentiment s = new CompanySentiment(
                "slug", "Name", SentimentLabel.NEUTRAL, 0, 0, 0, 0, 0, 0,
                "Summary", null, Instant.now());
        assertThat(s.articleSentiments()).isEmpty();
    }

    @Test
    void companySentiment_listIsDefensivelyCopied() {
        List<ArticleSentiment> mutable = new java.util.ArrayList<>();
        mutable.add(new ArticleSentiment("a1", "Title", SentimentLabel.POSITIVE, 0.9, "Good"));
        CompanySentiment s = new CompanySentiment(
                "slug", "Name", SentimentLabel.POSITIVE, 0.5, 1, 1, 0, 0, 0,
                "Summary", mutable, Instant.now());

        assertThatThrownBy(() -> s.articleSentiments().add(
                new ArticleSentiment("a2", "T2", SentimentLabel.NEUTRAL, 0.5, "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validArticleSentiment_createsSuccessfully() {
        ArticleSentiment a = new ArticleSentiment(
                "article-1", "Big Funding Round", SentimentLabel.POSITIVE, 0.92,
                "Raised $100M in Series C");

        assertThat(a.articleId()).isEqualTo("article-1");
        assertThat(a.sentiment()).isEqualTo(SentimentLabel.POSITIVE);
        assertThat(a.confidence()).isEqualTo(0.92);
    }

    @Test
    void articleSentiment_blankArticleId_throws() {
        assertThatThrownBy(() -> new ArticleSentiment(
                "", "Title", SentimentLabel.NEUTRAL, 0.5, "rationale"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleId");
    }

    @Test
    void articleSentiment_confidenceOutOfRange_throws() {
        assertThatThrownBy(() -> new ArticleSentiment(
                "a1", "Title", SentimentLabel.NEUTRAL, 1.5, "rationale"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void articleSentiment_nullSentiment_throws() {
        assertThatThrownBy(() -> new ArticleSentiment(
                "a1", "Title", null, 0.5, "rationale"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sentiment");
    }

    @Test
    void sentimentLabel_hasAllFourValues() {
        assertThat(SentimentLabel.values()).containsExactly(
                SentimentLabel.POSITIVE, SentimentLabel.NEGATIVE,
                SentimentLabel.MIXED, SentimentLabel.NEUTRAL);
    }
}
