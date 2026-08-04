package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;
import com.wgblackmon.aihealthcare.domain.port.outbound.PipelineRunEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA-backed implementation of {@link PipelineRunEventPort}.
 *
 * <p>Converts between the immutable {@link PipelineRunEvent} domain record
 * and the mutable {@link PipelineRunEventEntity} JPA entity.  All collection
 * transformations use traditional {@code for} loops per project convention.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class PipelineRunEventAdapter implements PipelineRunEventPort {

    private final PipelineRunEventRepository repository;

    public PipelineRunEventAdapter(PipelineRunEventRepository repository) {
        log.debug("PipelineRunEventAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(PipelineRunEvent event) {
        log.debug("save() | pipelineId={}, stepName={}, status={}",
                  event.pipelineId(), event.stepName(), event.status());

        PipelineRunEventEntity entity = PipelineRunEventEntity.fromDomain(event);
        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public List<PipelineRunEvent> findRecent(int limit) {
        log.debug("findRecent() | limit={}", limit);

        List<PipelineRunEventEntity> entities =
                repository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit));

        List<PipelineRunEvent> result = new ArrayList<>();
        for (PipelineRunEventEntity entity : entities) {
            result.add(entity.toDomain());
        }

        log.debug("findRecent() | return={} events", result.size());
        return result;
    }

    @Override
    public List<PipelineRunEvent> findByPipelineId(String pipelineId, int limit) {
        log.debug("findByPipelineId() | pipelineId={}, limit={}", pipelineId, limit);

        List<PipelineRunEventEntity> entities =
                repository.findByPipelineIdOrderByStartedAtDesc(pipelineId, PageRequest.of(0, limit));

        List<PipelineRunEvent> result = new ArrayList<>();
        for (PipelineRunEventEntity entity : entities) {
            result.add(entity.toDomain());
        }

        log.debug("findByPipelineId() | return={} events", result.size());
        return result;
    }

    @Override
    public Map<String, PipelineRunEvent> findLatestPerPipeline() {
        log.debug("findLatestPerPipeline()");

        List<PipelineRunEventEntity> entities = repository.findLatestPerPipeline();

        Map<String, PipelineRunEvent> result = new LinkedHashMap<>();
        for (PipelineRunEventEntity entity : entities) {
            PipelineRunEvent event = entity.toDomain();
            result.put(event.pipelineId(), event);
        }

        log.debug("findLatestPerPipeline() | return={} pipelines", result.size());
        return result;
    }
}
