package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionSnapshot;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ReactionHorizon;
import com.wgblackmon.aihealthcare.domain.marketanalysis.TrackedCompanyEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.PriceReactionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed adapter implementing {@link PriceReactionPort}.
 *
 * <p>{@link #findTickerEntriesPublishedAfter} joins {@link MarketDigestEntryJpaRepository}
 * and {@link MarketDigestAffectedCompanyJpaRepository} manually (no {@code @OneToMany},
 * consistent with the rest of the market-analysis persistence layer) to produce one
 * {@link TrackedCompanyEntry} per ticker-bearing affected company.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
@Slf4j
@Component
public class PriceReactionAdapter implements PriceReactionPort {

    private final PriceReactionSnapshotJpaRepository snapshotRepo;
    private final MarketDigestEntryJpaRepository entryRepo;
    private final MarketDigestAffectedCompanyJpaRepository companyRepo;

    public PriceReactionAdapter(PriceReactionSnapshotJpaRepository snapshotRepo,
                                 MarketDigestEntryJpaRepository entryRepo,
                                 MarketDigestAffectedCompanyJpaRepository companyRepo) {
        this.snapshotRepo = snapshotRepo;
        this.entryRepo = entryRepo;
        this.companyRepo = companyRepo;
    }

    @Override
    public void save(PriceReactionSnapshot snapshot) {
        log.debug("save() | entryId={}, tickerSymbol={}, horizon={}",
                snapshot.entryId(), snapshot.tickerSymbol(), snapshot.horizon());

        snapshotRepo.save(new PriceReactionSnapshotEntity(
                snapshot.entryId(),
                snapshot.tickerSymbol(),
                snapshot.horizon().name(),
                snapshot.baselinePrice(),
                snapshot.observedPrice(),
                snapshot.pctChange(),
                snapshot.measuredAt()
        ));

        log.debug("save() | return=void");
    }

    @Override
    public List<PriceReactionSnapshot> findByEntryId(String entryId) {
        log.debug("findByEntryId() | entryId={}", entryId);

        List<PriceReactionSnapshotEntity> entities = snapshotRepo.findByEntryId(entryId);
        List<PriceReactionSnapshot> results = new ArrayList<>();
        for (PriceReactionSnapshotEntity entity : entities) {
            results.add(toDomain(entity));
        }

        log.debug("findByEntryId() | return.size={}", results.size());
        return results;
    }

    @Override
    public boolean existsByEntryIdAndHorizon(String entryId, ReactionHorizon horizon) {
        log.debug("existsByEntryIdAndHorizon() | entryId={}, horizon={}", entryId, horizon);

        boolean result = snapshotRepo.existsByEntryIdAndHorizon(entryId, horizon.name());

        log.debug("existsByEntryIdAndHorizon() | return={}", result);
        return result;
    }

    @Override
    public List<TrackedCompanyEntry> findTickerEntriesPublishedAfter(Instant since) {
        log.debug("findTickerEntriesPublishedAfter() | since={}", since);

        List<MarketDigestEntryEntity> entries = entryRepo.findByPublishedAtAfter(since);
        List<TrackedCompanyEntry> results = new ArrayList<>();
        for (MarketDigestEntryEntity entry : entries) {
            List<MarketDigestAffectedCompanyEntity> companies = companyRepo.findByEntryId(entry.getEntryId());
            for (MarketDigestAffectedCompanyEntity company : companies) {
                if (company.getTickerSymbol() == null || company.getTickerSymbol().isBlank()) {
                    continue;
                }
                results.add(new TrackedCompanyEntry(
                        entry.getEntryId(),
                        company.getTickerSymbol(),
                        company.getCompanyName(),
                        entry.getPublishedAt()
                ));
            }
        }

        log.debug("findTickerEntriesPublishedAfter() | return.size={}", results.size());
        return results;
    }

    @Override
    public List<PriceReactionSnapshot> findByTickerAndPublishedAt(String tickerSymbol, Instant publishedAt) {
        log.debug("findByTickerAndPublishedAt() | tickerSymbol={}, publishedAt={}", tickerSymbol, publishedAt);

        List<MarketDigestEntryEntity> candidates = entryRepo.findByPublishedAt(publishedAt);
        for (MarketDigestEntryEntity entry : candidates) {
            List<MarketDigestAffectedCompanyEntity> companies = companyRepo.findByEntryId(entry.getEntryId());
            for (MarketDigestAffectedCompanyEntity company : companies) {
                if (tickerSymbol.equals(company.getTickerSymbol())) {
                    List<PriceReactionSnapshot> result = findByEntryId(entry.getEntryId());
                    log.debug("findByTickerAndPublishedAt() | return.size={}", result.size());
                    return result;
                }
            }
        }

        log.debug("findByTickerAndPublishedAt() | return.size=0 (no matching entry)");
        return new ArrayList<>();
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private PriceReactionSnapshot toDomain(PriceReactionSnapshotEntity entity) {
        return new PriceReactionSnapshot(
                entity.getEntryId(),
                entity.getTickerSymbol(),
                ReactionHorizon.valueOf(entity.getHorizon()),
                entity.getBaselinePrice(),
                entity.getObservedPrice(),
                entity.getPctChange(),
                entity.getMeasuredAt()
        );
    }
}
