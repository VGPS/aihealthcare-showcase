package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.LegalTrendSignal;
import com.wgblackmon.aihealthcare.domain.model.LegalTrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.outbound.LegalTrendSnapshotPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link LegalTrendSnapshotPort}.
 *
 * <p>Serializes and deserializes {@link LegalTrendSignal} lists to/from JSON
 * stored as CLOBs in the {@code legal_trend_snapshots} table. Uses the
 * Spring Boot auto-configured {@link ObjectMapper} with JavaTimeModule support.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@Slf4j
@Component
public class LegalTrendSnapshotAdapter implements LegalTrendSnapshotPort {

    private final LegalTrendSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public LegalTrendSnapshotAdapter(LegalTrendSnapshotRepository repository,
                                     ObjectMapper objectMapper) {
        log.debug("LegalTrendSnapshotAdapter() | repository={}, objectMapper={}",
                  repository.getClass().getSimpleName(), objectMapper.getClass().getSimpleName());
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(LegalTrendSnapshot snapshot) {
        log.debug("save() | generatedAt={}, totalKeywords={}",
                  snapshot.generatedAt(), snapshot.totalKeywords());

        LegalTrendSnapshotEntity entity = new LegalTrendSnapshotEntity();
        entity.setGeneratedAt(snapshot.generatedAt());
        entity.setWindowDays(snapshot.windowDays());
        entity.setRisingTrendsJson(toJson(snapshot.risingTrends()));
        entity.setTotalKeywords(snapshot.totalKeywords());

        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public Optional<LegalTrendSnapshot> findLatest() {
        log.debug("findLatest()");

        Optional<LegalTrendSnapshotEntity> entity = repository.findTopByOrderByGeneratedAtDesc();
        Optional<LegalTrendSnapshot> result = Optional.empty();

        if (entity.isPresent()) {
            result = Optional.of(toDomain(entity.get()));
        }

        log.debug("findLatest() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    private LegalTrendSnapshot toDomain(LegalTrendSnapshotEntity entity) {
        log.debug("toDomain() | id={}, generatedAt={}", entity.getId(), entity.getGeneratedAt());

        LegalTrendSnapshot result = new LegalTrendSnapshot(
                entity.getGeneratedAt(),
                entity.getWindowDays(),
                fromJson(entity.getRisingTrendsJson()),
                entity.getTotalKeywords()
        );

        log.debug("toDomain() | return={}", result.generatedAt());
        return result;
    }

    private String toJson(List<LegalTrendSignal> signals) {
        try {
            return objectMapper.writeValueAsString(signals);
        } catch (JsonProcessingException e) {
            log.error("toJson() | failed to serialize LegalTrendSignal list", e);
            return "[]";
        }
    }

    private List<LegalTrendSignal> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<LegalTrendSignal>>() {});
        } catch (JsonProcessingException e) {
            log.error("fromJson() | failed to deserialize LegalTrendSignal list", e);
            return List.of();
        }
    }
}
