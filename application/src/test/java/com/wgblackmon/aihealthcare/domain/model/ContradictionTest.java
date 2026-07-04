package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Contradiction} domain record.
 *
 * <p>Verifies compact constructor validation, round-trip field access
 * with two {@link SourceRef} lists, and defensive list copying.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
class ContradictionTest {

    private static final String  SLUG        = "fda-ai-guidance";
    private static final String  PRIOR_CLAIM = "FDA requires premarket review for all AI devices";
    private static final String  NEW_CLAIM   = "FDA proposes exempting low-risk AI devices from review";
    private static final Instant DETECTED_AT = Instant.parse("2026-07-04T12:00:00Z");

    private static final SourceRef PRIOR_REF = new SourceRef(
            "article-001", "FDA", LocalDate.of(2026, 3, 15), "premarket review required");
    private static final SourceRef NEW_REF   = new SourceRef(
            "article-042", "STAT News", LocalDate.of(2026, 7, 1), "low-risk exemption proposed");

    @Test
    void validContradiction_constructsSuccessfully() {
        Contradiction c = new Contradiction(
                SLUG, PRIOR_CLAIM, NEW_CLAIM,
                List.of(PRIOR_REF), List.of(NEW_REF), DETECTED_AT);

        assertThat(c.pageSlug()).isEqualTo(SLUG);
        assertThat(c.priorClaim()).isEqualTo(PRIOR_CLAIM);
        assertThat(c.newClaim()).isEqualTo(NEW_CLAIM);
        assertThat(c.priorSources()).containsExactly(PRIOR_REF);
        assertThat(c.newSources()).containsExactly(NEW_REF);
        assertThat(c.detectedAt()).isEqualTo(DETECTED_AT);
    }

    @Test
    void roundTrip_bothSourceLists_preserveContents() {
        List<SourceRef> priorRefs = List.of(PRIOR_REF);
        List<SourceRef> newRefs = List.of(NEW_REF);

        Contradiction c = new Contradiction(
                SLUG, PRIOR_CLAIM, NEW_CLAIM, priorRefs, newRefs, DETECTED_AT);

        assertThat(c.priorSources()).hasSize(1);
        assertThat(c.priorSources().get(0).articleId()).isEqualTo("article-001");
        assertThat(c.newSources()).hasSize(1);
        assertThat(c.newSources().get(0).articleId()).isEqualTo("article-042");
    }

    @Test
    void nullPageSlug_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Contradiction(
                null, PRIOR_CLAIM, NEW_CLAIM,
                List.of(PRIOR_REF), List.of(NEW_REF), DETECTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSlug");
    }

    @Test
    void malformedPageSlug_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Contradiction(
                "Bad Slug!", PRIOR_CLAIM, NEW_CLAIM,
                List.of(PRIOR_REF), List.of(NEW_REF), DETECTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSlug");
    }

    @Test
    void blankPriorClaim_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Contradiction(
                SLUG, "  ", NEW_CLAIM,
                List.of(PRIOR_REF), List.of(NEW_REF), DETECTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priorClaim");
    }

    @Test
    void blankNewClaim_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Contradiction(
                SLUG, PRIOR_CLAIM, "",
                List.of(PRIOR_REF), List.of(NEW_REF), DETECTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newClaim");
    }

    @Test
    void nullPriorSources_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Contradiction(
                SLUG, PRIOR_CLAIM, NEW_CLAIM,
                null, List.of(NEW_REF), DETECTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priorSources");
    }

    @Test
    void nullDetectedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Contradiction(
                SLUG, PRIOR_CLAIM, NEW_CLAIM,
                List.of(PRIOR_REF), List.of(NEW_REF), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("detectedAt");
    }

    @Test
    void emptySources_areAllowed() {
        Contradiction c = new Contradiction(
                SLUG, PRIOR_CLAIM, NEW_CLAIM,
                List.of(), List.of(), DETECTED_AT);

        assertThat(c.priorSources()).isEmpty();
        assertThat(c.newSources()).isEmpty();
    }
}
