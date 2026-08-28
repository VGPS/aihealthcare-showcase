package com.wgblackmon.aihealthcare.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * REST response record representing a single entry in a {@link MarketDigestResponse}.
 *
 * <p>Flattens the domain hierarchy ({@code MarketDigestEntry} → {@code MarketNewsItem})
 * into a single flat record for easy JSON serialization.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record MarketDigestEntryResponse(
        String headline,
        String summary,
        List<String> sourceUrls,
        Instant publishedAt,
        String category,
        Long dealSizeUsd,
        String factClassification,
        int rank,
        List<ImpactAssessmentResponse> impactAssessments,
        List<AffectedCompanyResponse> affectedCompanies
) {

    /**
     * Nested record for one impact assessment dimension.
     *
     * @author  Bill Blackmon
     * @version 1.0
     * @since   2026-08-19
     * @updated 2026-08-19
     */
    public record ImpactAssessmentResponse(
            String dimension,
            String direction,
            String rationale
    ) {}

    /**
     * Nested record for one affected company.
     *
     * @author  Bill Blackmon
     * @version 1.0
     * @since   2026-08-19
     * @updated 2026-08-28  added reactions field (price-reaction scoring)
     */
    public record AffectedCompanyResponse(
            String name,
            String tickerSymbol,
            String role,
            String peerGroup,
            List<PriceReactionResponse> reactions
    ) {}

    /**
     * Nested record for one captured price-reaction measurement on an affected company.
     *
     * @author  Bill Blackmon
     * @version 1.0
     * @since   2026-08-28
     * @updated 2026-08-28
     */
    public record PriceReactionResponse(
            String horizon,
            BigDecimal baselinePrice,
            BigDecimal observedPrice,
            BigDecimal pctChange,
            Instant measuredAt
    ) {}
}
