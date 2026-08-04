package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.FrameworkDimension;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrameworkAnalysisPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link FrameworkAnalysisPort}.
 *
 * <p>Serializes and deserializes structured fields (dimensions, strengths,
 * weaknesses, recent developments) to/from JSON stored as TEXT columns in
 * the {@code framework_analyses} table. Uses the Spring Boot auto-configured
 * {@link ObjectMapper}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@Component
public class FrameworkAnalysisPersistenceAdapter implements FrameworkAnalysisPort {

    private final FrameworkAnalysisRepository repository;
    private final ObjectMapper objectMapper;

    public FrameworkAnalysisPersistenceAdapter(FrameworkAnalysisRepository repository,
                                               ObjectMapper objectMapper) {
        log.debug("FrameworkAnalysisPersistenceAdapter() | repository={}, objectMapper={}",
                  repository.getClass().getSimpleName(), objectMapper.getClass().getSimpleName());
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(FrameworkAnalysis analysis) {
        log.debug("save() | companySlug={}, overallScore={}, articleCount={}",
                  analysis.companySlug(), analysis.overallScore(), analysis.articleCount());

        FrameworkAnalysisEntity entity = toEntity(analysis);
        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public Optional<FrameworkAnalysis> findBySlug(String companySlug) {
        log.debug("findBySlug() | companySlug={}", companySlug);

        Optional<FrameworkAnalysis> result = repository.findById(companySlug).map(this::toDomain);

        log.debug("findBySlug() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public List<FrameworkAnalysis> findAll() {
        log.debug("findAll()");

        List<FrameworkAnalysisEntity> entities = repository.findAllByOrderByOverallScoreDesc();
        List<FrameworkAnalysis> result = new ArrayList<>();
        for (FrameworkAnalysisEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findAll() | return={} analyses", result.size());
        return result;
    }

    private FrameworkAnalysisEntity toEntity(FrameworkAnalysis analysis) {
        FrameworkAnalysisEntity entity = new FrameworkAnalysisEntity();
        entity.setCompanySlug(analysis.companySlug());
        entity.setCompanyName(analysis.companyName());
        entity.setOverallAssessment(analysis.overallAssessment());
        entity.setDimensionsJson(toJson(analysis.dimensions()));
        entity.setStrengthsJson(toJson(analysis.strengths()));
        entity.setWeaknessesJson(toJson(analysis.weaknesses()));
        entity.setRecentDevelopmentsJson(toJson(analysis.recentDevelopments()));
        entity.setOverallScore(analysis.overallScore());
        entity.setArticleCount(analysis.articleCount());
        entity.setAnalyzedAt(analysis.analyzedAt());
        return entity;
    }

    private FrameworkAnalysis toDomain(FrameworkAnalysisEntity entity) {
        return new FrameworkAnalysis(
                entity.getCompanySlug(),
                entity.getCompanyName(),
                entity.getOverallAssessment(),
                dimensionsFromJson(entity.getDimensionsJson()),
                stringsFromJson(entity.getStrengthsJson()),
                stringsFromJson(entity.getWeaknessesJson()),
                stringsFromJson(entity.getRecentDevelopmentsJson()),
                entity.getOverallScore(),
                entity.getArticleCount(),
                entity.getAnalyzedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("toJson() | failed to serialize value: {}", e.getMessage());
            return "[]";
        }
    }

    private List<FrameworkDimension> dimensionsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<FrameworkDimension>>() {});
        } catch (JsonProcessingException e) {
            log.warn("dimensionsFromJson() | failed to deserialize dimensions: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> stringsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("stringsFromJson() | failed to deserialize string list: {}", e.getMessage());
            return List.of();
        }
    }
}
