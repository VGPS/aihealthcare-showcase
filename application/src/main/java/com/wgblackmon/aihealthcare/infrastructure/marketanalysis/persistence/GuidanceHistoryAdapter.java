package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.GuidanceComparison;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.GuidancePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link GuidancePort}.
 *
 * <p>Persists guidance comparisons to the {@code guidance_history} table and
 * retrieves the most recent row for a ticker+metric pair. Each call to
 * {@link #recordGuidance} appends a new row, preserving the full audit trail.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class GuidanceHistoryAdapter implements GuidancePort {

    private final GuidanceHistoryJpaRepository repo;

    public GuidanceHistoryAdapter(GuidanceHistoryJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<GuidanceComparison> getPriorGuidance(String tickerSymbol, String metric) {
        log.debug("getPriorGuidance() | ticker={}, metric={}", tickerSymbol, metric);

        Optional<GuidanceComparison> result = repo
                .findTopByTickerSymbolAndMetricOrderByRecordedAtDesc(tickerSymbol, metric)
                .map(this::toDomain);

        log.debug("getPriorGuidance() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public void recordGuidance(GuidanceComparison comparison) {
        log.debug("recordGuidance() | ticker={}, metric={}",
                comparison.tickerSymbol(), comparison.metric());

        GuidanceHistoryEntity entity = new GuidanceHistoryEntity(
                comparison.tickerSymbol(),
                comparison.priorGuidanceLow(),
                comparison.priorGuidanceHigh(),
                comparison.newGuidanceLow(),
                comparison.newGuidanceHigh(),
                comparison.metric(),
                Instant.now()
        );
        repo.save(entity);

        log.debug("recordGuidance() | return=void");
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private GuidanceComparison toDomain(GuidanceHistoryEntity entity) {
        return new GuidanceComparison(
                entity.getTickerSymbol(),
                entity.getPriorGuidanceLow(),
                entity.getPriorGuidanceHigh(),
                entity.getNewGuidanceLow(),
                entity.getNewGuidanceHigh(),
                entity.getMetric()
        );
    }
}
