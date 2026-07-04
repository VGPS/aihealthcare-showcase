package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link CompilationReport} domain record.
 *
 * <p>Verifies compact constructor validation, field access for key
 * collections (pagesCreated, contradictionsFlagged), and rejection
 * of invalid inputs.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
class CompilationReportTest {

    private static final Instant STARTED   = Instant.parse("2026-07-04T04:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-07-04T04:05:00Z");

    private static final Contradiction SAMPLE_CONTRADICTION = new Contradiction(
            "fda-ai-guidance",
            "All AI devices require premarket review",
            "Low-risk AI devices exempt from review",
            List.of(new SourceRef("a-001", "FDA", LocalDate.of(2026, 3, 15), null)),
            List.of(new SourceRef("a-042", "STAT", LocalDate.of(2026, 7, 1), null)),
            Instant.parse("2026-07-04T04:03:00Z"));

    @Test
    void validReport_constructsSuccessfully() {
        CompilationReport report = new CompilationReport(
                STARTED, COMPLETED, 25,
                List.of("fda-ai-guidance", "epic-ambient-ai"),
                List.of("google-medpalm"),
                List.of(SAMPLE_CONTRADICTION),
                List.of("Skipped article with missing body text"));

        assertThat(report.runStartedAt()).isEqualTo(STARTED);
        assertThat(report.runCompletedAt()).isEqualTo(COMPLETED);
        assertThat(report.articlesProcessed()).isEqualTo(25);
        assertThat(report.pagesCreated()).containsExactly("fda-ai-guidance", "epic-ambient-ai");
        assertThat(report.pagesUpdated()).containsExactly("google-medpalm");
        assertThat(report.contradictionsFlagged()).hasSize(1);
        assertThat(report.warnings()).containsExactly("Skipped article with missing body text");
    }

    @Test
    void pagesCreated_accessibleAfterConstruction() {
        CompilationReport report = new CompilationReport(
                STARTED, COMPLETED, 10,
                List.of("page-one", "page-two"),
                List.of(), List.of(), List.of());

        assertThat(report.pagesCreated()).containsExactly("page-one", "page-two");
    }

    @Test
    void contradictionsFlagged_accessibleAfterConstruction() {
        CompilationReport report = new CompilationReport(
                STARTED, COMPLETED, 5,
                List.of(), List.of(),
                List.of(SAMPLE_CONTRADICTION),
                List.of());

        assertThat(report.contradictionsFlagged()).hasSize(1);
        assertThat(report.contradictionsFlagged().get(0).pageSlug()).isEqualTo("fda-ai-guidance");
    }

    @Test
    void zeroArticlesProcessed_isAllowed() {
        CompilationReport report = new CompilationReport(
                STARTED, COMPLETED, 0,
                List.of(), List.of(), List.of(), List.of());

        assertThat(report.articlesProcessed()).isZero();
    }

    @Test
    void negativeArticlesProcessed_throwsIllegalArgument() {
        assertThatThrownBy(() -> new CompilationReport(
                STARTED, COMPLETED, -1,
                List.of(), List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articlesProcessed");
    }

    @Test
    void nullRunStartedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new CompilationReport(
                null, COMPLETED, 5,
                List.of(), List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runStartedAt");
    }

    @Test
    void nullRunCompletedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new CompilationReport(
                STARTED, null, 5,
                List.of(), List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runCompletedAt");
    }

    @Test
    void nullPagesCreated_throwsIllegalArgument() {
        assertThatThrownBy(() -> new CompilationReport(
                STARTED, COMPLETED, 5,
                null, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pagesCreated");
    }

    @Test
    void nullWarnings_throwsIllegalArgument() {
        assertThatThrownBy(() -> new CompilationReport(
                STARTED, COMPLETED, 5,
                List.of(), List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warnings");
    }

    @Test
    void emptyReport_isAllowed() {
        CompilationReport report = new CompilationReport(
                STARTED, COMPLETED, 0,
                List.of(), List.of(), List.of(), List.of());

        assertThat(report.pagesCreated()).isEmpty();
        assertThat(report.pagesUpdated()).isEmpty();
        assertThat(report.contradictionsFlagged()).isEmpty();
        assertThat(report.warnings()).isEmpty();
    }
}
