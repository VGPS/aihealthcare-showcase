package com.wgblackmon.aihealthcare.web.dto;

import java.math.BigDecimal;

/**
 * REST response record for deal term details of an M&amp;A or partnership entry.
 *
 * <p>Returned by {@code GET /api/market-digest/deal-terms/{headline}} endpoint.
 * All monetary and percentage fields are nullable because deal terms are
 * frequently only partially disclosed.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
public record DealTermsResponse(
        Long upfrontCashUsd,
        Long milestonePaymentsUsd,
        BigDecimal equityStakePct,
        BigDecimal royaltyPct,
        String disclosedPortion
) {}
