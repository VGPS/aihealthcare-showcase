package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.ArticleSentiment;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanySentimentPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link CompanySentimentPort}.
 *
 * <p>Serializes and deserializes {@link ArticleSentiment} lists to/from JSON
 * stored as a TEXT column in the {@code company_sentiments} table. Uses the
 * Spring Boot auto-configured {@link ObjectMapper}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@Component
public class CompanySentimentAdapter implements CompanySentimentPort {

    private final CompanySentimentRepository repository;
    private final ObjectMapper objectMapper;

    public CompanySentimentAdapter(CompanySentimentRepository repository,
                                   ObjectMapper objectMapper) {
        log.debug("CompanySentimentAdapter() | repository={}, objectMapper={}",
                  repository.getClass().getSimpleName(), objectMapper.getClass().getSimpleName());
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(CompanySentiment sentiment) {
        log.debug("save() | companySlug={}, overallSentiment={}, totalArticles={}",
                  sentiment.companySlug(), sentiment.overallSentiment(), sentiment.totalArticles());

        CompanySentimentEntity entity = toEntity(sentiment);
        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public Optional<CompanySentiment> findBySlug(String companySlug) {
        log.debug("findBySlug() | companySlug={}", companySlug);

        Optional<CompanySentiment> result = repository.findById(companySlug).map(this::toDomain);

        log.debug("findBySlug() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public List<CompanySentiment> findAll() {
        log.debug("findAll()");

        List<CompanySentimentEntity> entities = repository.findAllByOrderByAnalyzedAtDesc();
        List<CompanySentiment> result = new ArrayList<>();
        for (CompanySentimentEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findAll() | return={} sentiments", result.size());
        return result;
    }

    private CompanySentimentEntity toEntity(CompanySentiment sentiment) {
        CompanySentimentEntity entity = new CompanySentimentEntity();
        entity.setCompanySlug(sentiment.companySlug());
        entity.setCompanyName(sentiment.companyName());
        entity.setOverallSentiment(sentiment.overallSentiment().name());
        entity.setSentimentScore(sentiment.sentimentScore());
        entity.setTotalArticles(sentiment.totalArticles());
        entity.setPositiveCount(sentiment.positiveCount());
        entity.setNegativeCount(sentiment.negativeCount());
        entity.setMixedCount(sentiment.mixedCount());
        entity.setNeutralCount(sentiment.neutralCount());
        entity.setRiskSummary(sentiment.riskSummary());
        entity.setArticleSentimentsJson(toJson(sentiment.articleSentiments()));
        entity.setAnalyzedAt(sentiment.analyzedAt());
        return entity;
    }

    private CompanySentiment toDomain(CompanySentimentEntity entity) {
        return new CompanySentiment(
                entity.getCompanySlug(),
                entity.getCompanyName(),
                SentimentLabel.valueOf(entity.getOverallSentiment()),
                entity.getSentimentScore(),
                entity.getTotalArticles(),
                entity.getPositiveCount(),
                entity.getNegativeCount(),
                entity.getMixedCount(),
                entity.getNeutralCount(),
                entity.getRiskSummary(),
                fromJson(entity.getArticleSentimentsJson()),
                entity.getAnalyzedAt()
        );
    }

    private String toJson(List<ArticleSentiment> sentiments) {
        try {
            return objectMapper.writeValueAsString(sentiments);
        } catch (JsonProcessingException e) {
            log.error("toJson() | failed to serialize ArticleSentiment list", e);
            return "[]";
        }
    }

    private List<ArticleSentiment> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ArticleSentiment>>() {});
        } catch (JsonProcessingException e) {
            log.error("fromJson() | failed to deserialize ArticleSentiment list", e);
            return List.of();
        }
    }
}
