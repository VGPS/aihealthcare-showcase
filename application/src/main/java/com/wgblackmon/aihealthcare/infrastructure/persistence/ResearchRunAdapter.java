package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.ResearchRun;
import com.wgblackmon.aihealthcare.domain.port.outbound.ResearchRunPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link ResearchRunPort}.
 *
 * <p>Persists and retrieves {@link ResearchRun} domain records via
 * {@link ResearchRunRepository}.  All mapping between the immutable domain
 * record and the mutable {@link ResearchRunEntity} is performed inside this
 * adapter — entities never escape to the application or domain layers.
 *
 * <p>{@link #save} is idempotent: if a run with the same {@code runId} already
 * exists, {@code JpaRepository.save()} will perform an update (merge) rather
 * than inserting a duplicate row.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
@Slf4j
@Component
public class ResearchRunAdapter implements ResearchRunPort {

    private final ResearchRunRepository repository;

    /**
     * Constructs the adapter with its repository dependency.
     *
     * @param repository Spring Data JPA repository for research run entities.
     */
    public ResearchRunAdapter(ResearchRunRepository repository) {
        log.debug("ResearchRunAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
        log.debug("ResearchRunAdapter() | return=void");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses {@link ResearchRunRepository#save} which performs an INSERT or UPDATE
     * depending on whether the {@code runId} already exists in the table.
     */
    @Override
    public void save(ResearchRun run) {
        log.debug("save() | runId={}", run.runId());
        repository.save(toEntity(run));
        log.debug("save() | return=void");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns runs ordered by {@code researchedAt} descending (most recent first).
     */
    @Override
    public List<ResearchRun> findAll() {
        log.debug("findAll() |");
        List<ResearchRunEntity> entities =
                repository.findAll(Sort.by(Sort.Direction.DESC, "researchedAt"));
        List<ResearchRun> result = new ArrayList<>();
        for (ResearchRunEntity entity : entities) {
            result.add(toDomain(entity));
        }
        List<ResearchRun> unmodifiable = List.copyOf(result);
        log.debug("findAll() | return={} runs", unmodifiable.size());
        return unmodifiable;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ResearchRun> findByRunId(String runId) {
        log.debug("findByRunId() | runId={}", runId);
        Optional<ResearchRunEntity> entity = repository.findByRunId(runId);
        Optional<ResearchRun> result = entity.map(this::toDomain);
        log.debug("findByRunId() | return={}", result.isPresent() ? result.get().runId() : "empty");
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ResearchRunEntity toEntity(ResearchRun run) {
        log.debug("toEntity() | runId={}", run.runId());
        ResearchRunEntity entity = new ResearchRunEntity();
        entity.setRunId(run.runId());
        entity.setQuery(run.query());
        entity.setMode(run.mode());
        entity.setCitationCount(run.citationCount());
        entity.setResearchedAt(run.researchedAt());
        log.debug("toEntity() | return={}", entity.getRunId());
        return entity;
    }

    private ResearchRun toDomain(ResearchRunEntity entity) {
        log.debug("toDomain() | runId={}", entity.getRunId());
        ResearchRun result = new ResearchRun(
                entity.getRunId(),
                entity.getQuery(),
                entity.getMode(),
                entity.getCitationCount(),
                entity.getResearchedAt());
        log.debug("toDomain() | return={}", result.runId());
        return result;
    }
}
