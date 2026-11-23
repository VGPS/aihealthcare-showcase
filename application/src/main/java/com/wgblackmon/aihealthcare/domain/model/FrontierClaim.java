package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable domain record representing a specific public claim made by a
 * frontier AI company that has been extracted and classified by the LLM
 * Claim Tracker pipeline.
 *
 * <p>Each claim carries a {@link ClaimType} identifying its subject domain,
 * a {@link ClaimVerdict} rating the quality of the evidence behind it, and
 * an optional {@code evidenceNotes} field holding the LLM's one- to two-sentence
 * rationale for the verdict (clean text only — no structured prefix).
 *
 * <p>MARKETING_HYPE claims additionally carry {@code hypeSeverity} (MILD/MODERATE/SEVERE),
 * {@code triggeringPhrase} (the exact quoted phrase that drove the verdict), and
 * {@code deploymentContext} (CLINICAL/OPERATIONAL/RESEARCH/GENERAL).  These are null
 * for all other verdict types.
 *
 * <p>Claims link back to their source article via {@code articleId} and {@code sourceUrl}.
 *
 * <p>This record does not carry any Spring or Lombok dependencies — it is a
 * pure JDK type and safe to use anywhere in the hexagonal architecture.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-10-05
 * @updated 2026-11-24
 */
public record FrontierClaim(
        String claimId,
        String company,
        String claimText,
        LocalDate claimDate,
        String sourceUrl,
        String sourceTitle,
        ClaimType claimType,
        ClaimVerdict verdict,
        String evidenceNotes,
        HypeSeverity hypeSeverity,
        String triggeringPhrase,
        DeploymentContext deploymentContext,
        String articleId,
        Instant detectedAt,
        Instant lastReviewedAt
) {
    public FrontierClaim {
        if (claimId == null || claimId.isBlank()) {
            throw new IllegalArgumentException("claimId must not be blank");
        }
        if (company == null || company.isBlank()) {
            throw new IllegalArgumentException("company must not be blank");
        }
        if (claimText == null || claimText.isBlank()) {
            throw new IllegalArgumentException("claimText must not be blank");
        }
        if (claimType == null) {
            throw new IllegalArgumentException("claimType must not be null");
        }
        if (verdict == null) {
            throw new IllegalArgumentException("verdict must not be null");
        }
        if (detectedAt == null) {
            throw new IllegalArgumentException("detectedAt must not be null");
        }
    }
}
