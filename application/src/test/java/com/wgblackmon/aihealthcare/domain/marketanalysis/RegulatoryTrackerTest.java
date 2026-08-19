package com.wgblackmon.aihealthcare.domain.marketanalysis;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link RegulatoryTracker} domain record.
 *
 * <p>Covers field validation, nullable {@code commentDeadline}, and accessor
 * round-trips for both enum fields.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
class RegulatoryTrackerTest {

    private static final Instant NOW = Instant.now();

    @Test
    void validTracker_createsSuccessfully() {
        RegulatoryTracker t = new RegulatoryTracker(
                Jurisdiction.US_FDA,
                RulemakingStage.COMMENT_PERIOD,
                "FDA-2024-N-2177",
                "AI/ML-Based Software as a Medical Device Action Plan",
                LocalDate.of(2025, 3, 15),
                NOW
        );
        assertThat(t.jurisdiction()).isEqualTo(Jurisdiction.US_FDA);
        assertThat(t.stage()).isEqualTo(RulemakingStage.COMMENT_PERIOD);
        assertThat(t.docketId()).isEqualTo("FDA-2024-N-2177");
        assertThat(t.commentDeadline()).isEqualTo(LocalDate.of(2025, 3, 15));
    }

    @Test
    void nullCommentDeadline_isAllowed() {
        RegulatoryTracker t = new RegulatoryTracker(
                Jurisdiction.EU_AI_ACT,
                RulemakingStage.FINAL_GUIDANCE,
                "EU-2024-AIA-001",
                "EU AI Act High-Risk Classification",
                null,
                NOW
        );
        assertThat(t.commentDeadline()).isNull();
    }

    @Test
    void nullJurisdiction_throws() {
        assertThatThrownBy(() -> new RegulatoryTracker(
                null, RulemakingStage.COMMENT_PERIOD, "DOC-1", "Title", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jurisdiction");
    }

    @Test
    void nullStage_throws() {
        assertThatThrownBy(() -> new RegulatoryTracker(
                Jurisdiction.US_FDA, null, "DOC-1", "Title", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
    }

    @Test
    void blankDocketId_throws() {
        assertThatThrownBy(() -> new RegulatoryTracker(
                Jurisdiction.US_FDA, RulemakingStage.DRAFT_GUIDANCE, "  ", "Title", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docketId");
    }

    @Test
    void blankTitle_throws() {
        assertThatThrownBy(() -> new RegulatoryTracker(
                Jurisdiction.US_FDA, RulemakingStage.ENFORCEMENT, "DOC-1", "", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void nullLastUpdatedAt_throws() {
        assertThatThrownBy(() -> new RegulatoryTracker(
                Jurisdiction.US_FDA, RulemakingStage.COMMENT_PERIOD, "DOC-1", "Title", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastUpdatedAt");
    }

    @Test
    void allJurisdictions_roundTrip() {
        for (Jurisdiction j : Jurisdiction.values()) {
            RegulatoryTracker t = new RegulatoryTracker(
                    j, RulemakingStage.DISCUSSION_PAPER, "DOC-" + j.name(), "Title", null, NOW);
            assertThat(t.jurisdiction()).isEqualTo(j);
        }
    }
}
