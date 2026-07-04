package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link SourceRef} domain record.
 *
 * <p>Verifies that the compact constructor enforces required field constraints
 * and that the optional {@code excerpt} field accepts {@code null}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
class SourceRefTest {

    private static final String    ARTICLE_ID   = "article-001";
    private static final String    SOURCE_NAME  = "FDA";
    private static final LocalDate HARVESTED_ON = LocalDate.of(2026, 7, 4);
    private static final String    EXCERPT      = "AI devices require premarket review.";

    @Test
    void validSourceRef_constructsSuccessfully() {
        SourceRef ref = new SourceRef(ARTICLE_ID, SOURCE_NAME, HARVESTED_ON, EXCERPT);

        assertThat(ref.articleId()).isEqualTo(ARTICLE_ID);
        assertThat(ref.sourceName()).isEqualTo(SOURCE_NAME);
        assertThat(ref.harvestedOn()).isEqualTo(HARVESTED_ON);
        assertThat(ref.excerpt()).isEqualTo(EXCERPT);
    }

    @Test
    void nullExcerpt_isAllowed() {
        SourceRef ref = new SourceRef(ARTICLE_ID, SOURCE_NAME, HARVESTED_ON, null);

        assertThat(ref.excerpt()).isNull();
    }

    @Test
    void nullArticleId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SourceRef(null, SOURCE_NAME, HARVESTED_ON, EXCERPT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleId");
    }

    @Test
    void blankArticleId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SourceRef("  ", SOURCE_NAME, HARVESTED_ON, EXCERPT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleId");
    }

    @Test
    void nullSourceName_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SourceRef(ARTICLE_ID, null, HARVESTED_ON, EXCERPT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceName");
    }

    @Test
    void blankSourceName_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SourceRef(ARTICLE_ID, "", HARVESTED_ON, EXCERPT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceName");
    }

    @Test
    void nullHarvestedOn_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SourceRef(ARTICLE_ID, SOURCE_NAME, null, EXCERPT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("harvestedOn");
    }
}
