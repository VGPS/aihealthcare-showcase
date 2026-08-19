package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AnalystRatingChange;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.AnalystRatingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed adapter implementing {@link AnalystRatingPort}.
 *
 * <p>Persists each analyst rating change as an independent row in
 * {@code analyst_rating_change} — no upsert, full audit trail.
 * Price targets are nullable and passed through as-is.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class AnalystRatingChangeAdapter implements AnalystRatingPort {

    private final AnalystRatingChangeJpaRepository repo;

    public AnalystRatingChangeAdapter(AnalystRatingChangeJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public void save(AnalystRatingChange change) {
        log.debug("save() | firm={}, ticker={}, {} -> {}",
                change.firm(), change.tickerSymbol(),
                change.previousRating(), change.newRating());

        AnalystRatingChangeEntity entity = new AnalystRatingChangeEntity(
                change.firm(),
                change.tickerSymbol(),
                change.previousRating(),
                change.newRating(),
                change.previousPriceTarget(),
                change.newPriceTarget(),
                change.changedAt()
        );
        repo.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public List<AnalystRatingChange> findRecentChanges(String tickerSymbol, Instant since) {
        log.debug("findRecentChanges() | ticker={}, since={}", tickerSymbol, since);

        List<AnalystRatingChangeEntity> entities =
                repo.findByTickerSymbolAndChangedAtGreaterThanEqualOrderByChangedAtDesc(tickerSymbol, since);

        List<AnalystRatingChange> result = new ArrayList<>();
        for (AnalystRatingChangeEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findRecentChanges() | return.size={}", result.size());
        return result;
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private AnalystRatingChange toDomain(AnalystRatingChangeEntity entity) {
        return new AnalystRatingChange(
                entity.getFirm(),
                entity.getTickerSymbol(),
                entity.getPreviousRating(),
                entity.getNewRating(),
                entity.getPreviousPriceTarget(),
                entity.getNewPriceTarget(),
                entity.getChangedAt()
        );
    }
}
