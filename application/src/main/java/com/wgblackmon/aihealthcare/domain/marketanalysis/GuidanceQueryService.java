package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.GuidancePort;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Domain service for guidance history operations.
 *
 * <p>Thin orchestration layer that decouples the web controller from the
 * {@link GuidancePort} outbound port, satisfying the hexagonal-architecture rule
 * that controllers must only call services, never ports directly.
 *
 * <p>Pure Java — no Spring or Lombok imports, consistent with domain-module
 * purity constraints.  Instantiated as a {@code @Bean} in {@code MarketAnalysisConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public class GuidanceQueryService {

    private static final Logger log =
            Logger.getLogger(GuidanceQueryService.class.getName());

    private final GuidancePort guidancePort;

    public GuidanceQueryService(GuidancePort guidancePort) {
        this.guidancePort = guidancePort;
    }

    /**
     * Returns the most recently recorded guidance for the given ticker and metric,
     * or empty if no history exists.
     */
    public Optional<GuidanceComparison> findLatestGuidance(String ticker, String metric) {
        log.fine(() -> "findLatestGuidance() | ticker=" + ticker + ", metric=" + metric);
        Optional<GuidanceComparison> result = guidancePort.getPriorGuidance(ticker, metric);
        log.fine(() -> "findLatestGuidance() | return=" + (result.isPresent() ? "present" : "empty"));
        return result;
    }

    /**
     * Persists a new guidance comparison for future pipeline lookups.
     */
    public void record(GuidanceComparison comparison) {
        log.fine(() -> "record() | ticker=" + comparison.tickerSymbol()
                + ", metric=" + comparison.metric());
        guidancePort.recordGuidance(comparison);
        log.fine("record() | return=void");
    }
}
