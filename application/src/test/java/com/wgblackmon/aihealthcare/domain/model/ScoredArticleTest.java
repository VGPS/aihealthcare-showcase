package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ScoredArticle} domain record validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-24
 * @updated 2026-07-24
 */
class ScoredArticleTest {

    @Test
    void validScoredArticle_createsSuccessfully() {
        ScoredArticle sa = new ScoredArticle("art-1", "FDA clears AI diagnostic", 8,
                "First autonomous AI diagnostic cleared by FDA", "radiology ai");

        assertThat(sa.articleId()).isEqualTo("art-1");
        assertThat(sa.title()).isEqualTo("FDA clears AI diagnostic");
        assertThat(sa.score()).isEqualTo(8);
        assertThat(sa.rationale()).isEqualTo("First autonomous AI diagnostic cleared by FDA");
        assertThat(sa.keyword()).isEqualTo("radiology ai");
    }

    @Test
    void nullArticleId_throwsException() {
        assertThatThrownBy(() -> new ScoredArticle(null, "Title", 7, "Rationale", "keyword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleId");
    }

    @Test
    void blankTitle_throwsException() {
        assertThatThrownBy(() -> new ScoredArticle("art-1", "  ", 7, "Rationale", "keyword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void scoreBelowRange_throwsException() {
        assertThatThrownBy(() -> new ScoredArticle("art-1", "Title", 0, "Rationale", "keyword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
    }

    @Test
    void scoreAboveRange_throwsException() {
        assertThatThrownBy(() -> new ScoredArticle("art-1", "Title", 11, "Rationale", "keyword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
    }

    @Test
    void blankRationale_throwsException() {
        assertThatThrownBy(() -> new ScoredArticle("art-1", "Title", 7, "", "keyword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rationale");
    }

    @Test
    void nullKeyword_throwsException() {
        assertThatThrownBy(() -> new ScoredArticle("art-1", "Title", 7, "Rationale", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword");
    }

    @Test
    void boundaryScore_1_isValid() {
        ScoredArticle sa = new ScoredArticle("art-1", "Title", 1, "Low relevance", "keyword");
        assertThat(sa.score()).isEqualTo(1);
    }

    @Test
    void boundaryScore_10_isValid() {
        ScoredArticle sa = new ScoredArticle("art-1", "Title", 10, "Landmark event", "keyword");
        assertThat(sa.score()).isEqualTo(10);
    }
}
