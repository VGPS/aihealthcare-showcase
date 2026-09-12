package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.Jurisdiction;
import com.wgblackmon.aihealthcare.domain.marketanalysis.RegulatoryTracker;
import com.wgblackmon.aihealthcare.domain.marketanalysis.RulemakingStage;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.RegulatoryTrackerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link RegulatoryTrackerRepository}.
 *
 * <p>The {@link #upsert} operation deletes any existing row for the same
 * {@code docketId + jurisdiction} pair before inserting a fresh entity.
 * This replaces the current-state row without accumulating history
 * (history is in the source news articles, not in this table).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class RegulatoryTrackerRepositoryAdapter implements RegulatoryTrackerRepository {

    private final RegulatoryTrackerJpaRepository repo;

    public RegulatoryTrackerRepositoryAdapter(RegulatoryTrackerJpaRepository repo) {
        this.repo = repo;
    }

    @Transactional
    @Override
    public void upsert(RegulatoryTracker tracker) {
        log.debug("upsert() | docketId={}, jurisdiction={}", tracker.docketId(), tracker.jurisdiction());

        Optional<RegulatoryTrackerEntity> existing =
                repo.findByDocketIdAndJurisdiction(
                        tracker.docketId(), tracker.jurisdiction().name());
        if (existing.isPresent()) {
            log.debug("upsert() | deleting existing row id={}", existing.get().getId());
            repo.delete(existing.get());
            repo.flush();  // flush delete before insert — avoids unique-constraint violation
        }

        RegulatoryTrackerEntity entity = new RegulatoryTrackerEntity(
                tracker.jurisdiction().name(),
                tracker.stage().name(),
                tracker.docketId(),
                tracker.title(),
                tracker.commentDeadline(),
                tracker.lastUpdatedAt(),
                tracker.sourceUrl()
        );
        repo.save(entity);

        log.debug("upsert() | return=void");
    }

    @Override
    public Optional<RegulatoryTracker> findByDocketId(String docketId, Jurisdiction jurisdiction) {
        log.debug("findByDocketId() | docketId={}, jurisdiction={}", docketId, jurisdiction);

        Optional<RegulatoryTracker> result =
                repo.findByDocketIdAndJurisdiction(docketId, jurisdiction.name())
                        .map(this::toDomain);

        log.debug("findByDocketId() | return=present:{}", result.isPresent());
        return result;
    }

    @Override
    public List<RegulatoryTracker> findApproachingDeadlines(LocalDate deadlineOnOrBefore) {
        log.debug("findApproachingDeadlines() | deadlineOnOrBefore={}", deadlineOnOrBefore);

        List<RegulatoryTrackerEntity> entities =
                repo.findByCommentDeadlineLessThanEqualOrderByCommentDeadlineAsc(deadlineOnOrBefore);

        List<RegulatoryTracker> result = new ArrayList<>();
        for (RegulatoryTrackerEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findApproachingDeadlines() | return=count:{}", result.size());
        return result;
    }

    @Override
    public List<RegulatoryTracker> findAll() {
        log.debug("findAll()");

        List<RegulatoryTrackerEntity> entities = repo.findAllByOrderByLastUpdatedAtDesc();
        List<RegulatoryTracker> result = new ArrayList<>();
        for (RegulatoryTrackerEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findAll() | return=count:{}", result.size());
        return result;
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private RegulatoryTracker toDomain(RegulatoryTrackerEntity entity) {
        return new RegulatoryTracker(
                Jurisdiction.valueOf(entity.getJurisdiction()),
                RulemakingStage.valueOf(entity.getStage()),
                entity.getDocketId(),
                entity.getTitle(),
                entity.getCommentDeadline(),
                entity.getLastUpdatedAt(),
                entity.getSourceUrl()
        );
    }
}
