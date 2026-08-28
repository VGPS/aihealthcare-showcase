package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link TrendSnapshotPort}.
 *
 * <p>Serializes and deserializes {@link TrendSignal} lists to/from JSON
 * stored as CLOBs in the {@code trend_snapshots} table.  Uses the Spring Boot
 * auto-configured {@link ObjectMapper} with JavaTimeModule support.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-08-28
 */
@Slf4j
@Component
public class TrendSnapshotAdapter implements TrendSnapshotPort {

    private final TrendSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public TrendSnapshotAdapter(TrendSnapshotRepository repository, ObjectMapper objectMapper) {
        log.debug("TrendSnapshotAdapter() | repository={}, objectMapper={}",
                  repository.getClass().getSimpleName(), objectMapper.getClass().getSimpleName());
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(TrendSnapshot snapshot) {
        log.debug("save() | generatedAt={}, totalKeywords={}", snapshot.generatedAt(), snapshot.totalKeywords());

        TrendSnapshotEntity entity = new TrendSnapshotEntity();
        entity.setGeneratedAt(snapshot.generatedAt());
        entity.setWindowDays(snapshot.windowDays());
        entity.setRisingJson(toJson(snapshot.risingTopics()));
        entity.setFadingJson(toJson(snapshot.fadingTopics()));
        entity.setNewJson(toJson(snapshot.newTopics()));
        entity.setTotalKeywords(snapshot.totalKeywords());

        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public Optional<TrendSnapshot> findLatest() {
        log.debug("findLatest()");

        Optional<TrendSnapshotEntity> entity = repository.findTopByOrderByGeneratedAtDesc();
        Optional<TrendSnapshot> result = entity.map(this::toDomain);

        log.debug("findLatest() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public List<TrendSnapshot> findAll() {
        log.debug("findAll()");

        List<TrendSnapshotEntity> entities = repository.findAllByOrderByGeneratedAtDesc();
        List<TrendSnapshot> result = new ArrayList<>();
        for (TrendSnapshotEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findAll() | return={} snapshots", result.size());
        return result;
    }

    private TrendSnapshot toDomain(TrendSnapshotEntity entity) {
        log.debug("toDomain() | id={}, generatedAt={}", entity.getId(), entity.getGeneratedAt());

        TrendSnapshot result = new TrendSnapshot(
                entity.getGeneratedAt(),
                entity.getWindowDays(),
                fromJson(entity.getRisingJson()),
                fromJson(entity.getFadingJson()),
                fromJson(entity.getNewJson()),
                entity.getTotalKeywords()
        );

        log.debug("toDomain() | return={}", result.generatedAt());
        return result;
    }

    private String toJson(List<TrendSignal> signals) {
        try {
            return objectMapper.writeValueAsString(signals);
        } catch (JsonProcessingException e) {
            log.error("toJson() | failed to serialize TrendSignal list", e);
            return "[]";
        }
    }

    private List<TrendSignal> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<TrendSignal> result = objectMapper.readValue(json, new TypeReference<List<TrendSignal>>() {});
            log.debug("fromJson() | deserialized {} signals from {} chars", result.size(), json.length());
            return result;
        } catch (JsonProcessingException e) {
            log.error("fromJson() | failed to deserialize TrendSignal list (json length={})", json.length(), e);
            return List.of();
        }
    }
}
