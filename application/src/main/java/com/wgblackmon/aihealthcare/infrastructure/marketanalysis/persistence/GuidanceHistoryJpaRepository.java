package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link GuidanceHistoryEntity}.
 *
 * <p>The derived query fetches the most recent guidance comparison for a
 * given ticker+metric pair — used by {@link GuidanceHistoryAdapter} to
 * satisfy {@code GuidancePort.getPriorGuidance()}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface GuidanceHistoryJpaRepository
        extends JpaRepository<GuidanceHistoryEntity, Long> {

    Optional<GuidanceHistoryEntity>
    findTopByTickerSymbolAndMetricOrderByRecordedAtDesc(String tickerSymbol, String metric);
}
