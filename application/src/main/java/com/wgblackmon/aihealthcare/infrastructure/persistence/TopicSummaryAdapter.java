package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.TopicSummary;
import com.wgblackmon.aihealthcare.domain.port.outbound.TopicSummaryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link TopicSummaryPort}.
 *
 * <p>Persists and retrieves {@link TopicSummary} domain records via
 * {@link TopicSummaryRepository}.  The topic name is the primary key,
 * so {@code save()} acts as an upsert — the latest summary always wins.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-21
 * @updated 2026-05-21
 */
@Slf4j
@Component
public class TopicSummaryAdapter implements TopicSummaryPort {

    private final TopicSummaryRepository repository;

    public TopicSummaryAdapter(TopicSummaryRepository repository) {
        log.debug("TopicSummaryAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(TopicSummary summary) {
        log.debug("save() | topic={}", summary.topic());
        repository.save(toEntity(summary));
        log.debug("save() | return=void");
    }

    @Override
    public Optional<TopicSummary> findByTopic(String topic) {
        log.debug("findByTopic() | topic={}", topic);
        Optional<TopicSummary> result = repository.findById(topic).map(this::toDomain);
        log.debug("findByTopic() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public List<TopicSummary> findAll() {
        log.debug("findAll() |");
        List<TopicSummaryEntity> entities = repository.findAll();
        List<TopicSummary> result = new ArrayList<>();
        for (TopicSummaryEntity entity : entities) {
            result.add(toDomain(entity));
        }
        List<TopicSummary> unmodifiable = List.copyOf(result);
        log.debug("findAll() | return={} summaries", unmodifiable.size());
        return unmodifiable;
    }

    private TopicSummaryEntity toEntity(TopicSummary summary) {
        log.debug("toEntity() | topic={}", summary.topic());
        TopicSummaryEntity entity = new TopicSummaryEntity();
        entity.setTopic(summary.topic());
        entity.setSummaryText(summary.summaryText());
        entity.setGeneratedAt(summary.generatedAt());
        log.debug("toEntity() | return={}", entity.getTopic());
        return entity;
    }

    private TopicSummary toDomain(TopicSummaryEntity entity) {
        log.debug("toDomain() | topic={}", entity.getTopic());
        TopicSummary result = new TopicSummary(
                entity.getTopic(),
                entity.getSummaryText(),
                entity.getGeneratedAt()
        );
        log.debug("toDomain() | return={}", result.topic());
        return result;
    }
}
