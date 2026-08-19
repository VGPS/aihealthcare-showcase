package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.math.BigDecimal;

/**
 * Financial structure detail for an M&amp;A or partnership deal in a market digest entry.
 *
 * <p>All monetary and percentage fields are nullable because deal terms are frequently
 * only partially disclosed (see {@link DisclosedPortion}). Callers should check
 * {@code disclosedPortion} before relying on the numeric fields.
 *
 * @param upfrontCashUsd        cash paid at closing, in USD (nullable)
 * @param milestonePaymentsUsd  total contingent milestone payments, in USD (nullable)
 * @param equityStakePct        equity stake acquired, as a percentage 0–100 (nullable)
 * @param royaltyPct            royalty rate, as a percentage (nullable)
 * @param disclosedPortion      how much of the above is publicly available (required)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record DealTerms(
        Long upfrontCashUsd,
        Long milestonePaymentsUsd,
        BigDecimal equityStakePct,
        BigDecimal royaltyPct,
        DisclosedPortion disclosedPortion
) {
    public DealTerms {
        if (disclosedPortion == null) {
            throw new IllegalArgumentException("disclosedPortion must not be null");
        }
        if (equityStakePct != null
                && (equityStakePct.compareTo(BigDecimal.ZERO) < 0
                || equityStakePct.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("equityStakePct must be between 0 and 100");
        }
    }
}
