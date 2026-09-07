package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable record representing a private-company funding round in AI healthcare.
 *
 * <p>Captured when the market digest pipeline encounters FUNDING news items that
 * describe private (non-publicly traded) companies. Rounds are classified by
 * peer group at save time using {@link PeerGroupTagger} so they can be surfaced
 * as read-through signals alongside public peers in the same category.
 *
 * <p>{@code amountUsd} is nullable — many rounds are announced without disclosing
 * the exact raise amount. {@code leadInvestors} is required but may be empty when
 * investor names are not disclosed.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-09-07
 */
public record PrivateFundingRound(
        String companyName,
        String roundStage,
        Long amountUsd,
        List<String> leadInvestors,
        Instant announcedAt,
        String sourceUrl
) {
    public PrivateFundingRound {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName must not be blank");
        }
        if (roundStage == null || roundStage.isBlank()) {
            throw new IllegalArgumentException("roundStage must not be blank");
        }
        if (leadInvestors == null) {
            throw new IllegalArgumentException("leadInvestors must not be null (pass empty list if undisclosed)");
        }
        if (announcedAt == null) {
            throw new IllegalArgumentException("announcedAt must not be null");
        }
        leadInvestors = new ArrayList<>(leadInvestors);
    }
}
