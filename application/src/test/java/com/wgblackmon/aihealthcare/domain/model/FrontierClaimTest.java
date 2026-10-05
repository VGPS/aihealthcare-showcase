package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FrontierClaim} record validation and {@link ClaimType} /
 * {@link ClaimVerdict} enum coverage.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
class FrontierClaimTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    void validClaim_constructsSuccessfully() {
        FrontierClaim claim = buildValid();
        assertThat(claim.claimId()).isEqualTo("id-1");
        assertThat(claim.company()).isEqualTo("OpenAI");
        assertThat(claim.claimType()).isEqualTo(ClaimType.CAPABILITY_CLAIM);
        assertThat(claim.verdict()).isEqualTo(ClaimVerdict.MARKETING_HYPE);
        assertThat(claim.detectedAt()).isEqualTo(NOW);
    }

    @Test
    void nullClaimId_throws() {
        assertThatThrownBy(() -> new FrontierClaim(
                null, "OpenAI", "GPT-5 is perfect.",
                null, null, null,
                ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE,
                null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimId");
    }

    @Test
    void blankClaimId_throws() {
        assertThatThrownBy(() -> new FrontierClaim(
                "  ", "OpenAI", "GPT-5 is perfect.",
                null, null, null,
                ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE,
                null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimId");
    }

    @Test
    void nullCompany_throws() {
        assertThatThrownBy(() -> new FrontierClaim(
                "id-1", null, "GPT-5 is perfect.",
                null, null, null,
                ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE,
                null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("company");
    }

    @Test
    void nullClaimText_throws() {
        assertThatThrownBy(() -> new FrontierClaim(
                "id-1", "OpenAI", null,
                null, null, null,
                ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE,
                null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimText");
    }

    @Test
    void nullClaimType_throws() {
        assertThatThrownBy(() -> new FrontierClaim(
                "id-1", "OpenAI", "GPT-5 is perfect.",
                null, null, null,
                null, ClaimVerdict.MARKETING_HYPE,
                null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimType");
    }

    @Test
    void nullVerdict_throws() {
        assertThatThrownBy(() -> new FrontierClaim(
                "id-1", "OpenAI", "GPT-5 is perfect.",
                null, null, null,
                ClaimType.CAPABILITY_CLAIM, null,
                null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verdict");
    }

    @Test
    void allClaimTypeValues_areValid() {
        assertThat(ClaimType.values()).containsExactlyInAnyOrder(
                ClaimType.CAPABILITY_CLAIM,
                ClaimType.SAFETY_CLAIM,
                ClaimType.BENCHMARK_CLAIM,
                ClaimType.TIMELINE_CLAIM,
                ClaimType.REGULATORY_CLAIM,
                ClaimType.PARTNERSHIP_CLAIM
        );
    }

    @Test
    void allClaimVerdictValues_areValid() {
        assertThat(ClaimVerdict.values()).containsExactlyInAnyOrder(
                ClaimVerdict.EVIDENCE_BACKED,
                ClaimVerdict.ALLEGED_UNVERIFIED,
                ClaimVerdict.MARKETING_HYPE,
                ClaimVerdict.CONTRADICTED,
                ClaimVerdict.RETRACTED
        );
    }

    @Test
    void nullableFields_acceptNull() {
        FrontierClaim claim = new FrontierClaim(
                "id-1", "Anthropic", "Claude 4 leads all benchmarks.",
                null, null, null,
                ClaimType.BENCHMARK_CLAIM, ClaimVerdict.ALLEGED_UNVERIFIED,
                null, null, NOW, null);
        assertThat(claim.claimDate()).isNull();
        assertThat(claim.sourceUrl()).isNull();
        assertThat(claim.evidenceNotes()).isNull();
        assertThat(claim.articleId()).isNull();
        assertThat(claim.lastReviewedAt()).isNull();
    }

    private FrontierClaim buildValid() {
        return new FrontierClaim(
                "id-1", "OpenAI", "GPT-5 is perfect.",
                null, "https://openai.com/blog", "OpenAI Blog",
                ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE,
                "No independent verification cited.", "art-123", NOW, null);
    }
}
