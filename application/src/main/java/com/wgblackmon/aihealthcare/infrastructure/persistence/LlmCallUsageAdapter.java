package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.LlmCallUsage;
import com.wgblackmon.aihealthcare.domain.port.outbound.LlmCallUsagePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link LlmCallUsagePort}.
 *
 * <p>Converts between the immutable {@link LlmCallUsage} domain record and
 * the mutable {@link LlmCallUsageEntity} JPA entity.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@Slf4j
@Component
public class LlmCallUsageAdapter implements LlmCallUsagePort {

    private final LlmCallUsageRepository repository;

    public LlmCallUsageAdapter(LlmCallUsageRepository repository) {
        log.debug("LlmCallUsageAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(LlmCallUsage usage) {
        log.debug("save() | pipelineId={}, model={}, totalTokens={}",
                  usage.pipelineId(), usage.model(), usage.totalTokens());

        LlmCallUsageEntity entity = LlmCallUsageEntity.fromDomain(usage);
        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public List<LlmCallUsage> findSince(Instant since) {
        log.debug("findSince() | since={}", since);

        List<LlmCallUsageEntity> entities = repository.findByOccurredAtAfterOrderByOccurredAtDesc(since);

        List<LlmCallUsage> result = new ArrayList<>();
        for (LlmCallUsageEntity entity : entities) {
            result.add(entity.toDomain());
        }

        log.debug("findSince() | return={} rows", result.size());
        return result;
    }
}
