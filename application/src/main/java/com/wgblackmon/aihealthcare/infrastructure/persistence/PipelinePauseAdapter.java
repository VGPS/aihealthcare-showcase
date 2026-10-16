package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.port.outbound.PipelinePausePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Persistence adapter for the pipeline pause kill switch.
 *
 * <p>Absence of a row for a given {@code pipelineKey} means not paused —
 * the first {@link #setPaused} call for a key creates its row.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-16
 * @updated 2026-10-16
 */
@Slf4j
@Component
public class PipelinePauseAdapter implements PipelinePausePort {

    private final PipelinePauseRepository repository;

    public PipelinePauseAdapter(PipelinePauseRepository repository) {
        log.debug("PipelinePauseAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public boolean isPaused(String pipelineKey) {
        log.debug("isPaused() | pipelineKey={}", pipelineKey);
        boolean result = repository.findById(pipelineKey)
                .map(PipelinePauseEntity::isPaused)
                .orElse(false);
        log.debug("isPaused() | return={}", result);
        return result;
    }

    @Override
    public void setPaused(String pipelineKey, boolean paused) {
        log.debug("setPaused() | pipelineKey={}, paused={}", pipelineKey, paused);
        PipelinePauseEntity entity = repository.findById(pipelineKey)
                .orElseGet(() -> {
                    PipelinePauseEntity newEntity = new PipelinePauseEntity();
                    newEntity.setPipelineKey(pipelineKey);
                    return newEntity;
                });
        entity.setPaused(paused);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        log.debug("setPaused() | return=void");
    }
}
