package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CompanySentimentAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
class CompanySentimentAdapterTest {

    private CompanySentimentRepository repository;
    private CompanySentimentAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository = mock(CompanySentimentRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        adapter = new CompanySentimentAdapter(repository, objectMapper);
    }

    @Test
    void save_persistsEntity() {
        CompanySentiment sentiment = buildSentiment("tempus-ai", "Tempus AI",
                SentimentLabel.POSITIVE, 0.6);

        adapter.save(sentiment);

        ArgumentCaptor<CompanySentimentEntity> captor = ArgumentCaptor.forClass(CompanySentimentEntity.class);
        verify(repository).save(captor.capture());

        CompanySentimentEntity entity = captor.getValue();
        assertThat(entity.getCompanySlug()).isEqualTo("tempus-ai");
        assertThat(entity.getCompanyName()).isEqualTo("Tempus AI");
        assertThat(entity.getOverallSentiment()).isEqualTo("POSITIVE");
        assertThat(entity.getSentimentScore()).isEqualTo(0.6);
        assertThat(entity.getTotalArticles()).isEqualTo(10);
        assertThat(entity.getPositiveCount()).isEqualTo(5);
        assertThat(entity.getNegativeCount()).isEqualTo(2);
    }

    @Test
    void findBySlug_returnsDomainObject() {
        CompanySentimentEntity entity = buildEntity("test-co", "Test Co",
                "NEGATIVE", -0.5);
        when(repository.findById("test-co")).thenReturn(Optional.of(entity));

        Optional<CompanySentiment> result = adapter.findBySlug("test-co");

        assertThat(result).isPresent();
        assertThat(result.get().companySlug()).isEqualTo("test-co");
        assertThat(result.get().overallSentiment()).isEqualTo(SentimentLabel.NEGATIVE);
        assertThat(result.get().sentimentScore()).isEqualTo(-0.5);
    }

    @Test
    void findBySlug_returnsEmptyForMissing() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        Optional<CompanySentiment> result = adapter.findBySlug("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_returnsOrderedList() {
        CompanySentimentEntity e1 = buildEntity("new-co", "New Co", "POSITIVE", 0.5);
        e1.setAnalyzedAt(Instant.parse("2026-06-01T00:00:00Z"));
        CompanySentimentEntity e2 = buildEntity("old-co", "Old Co", "NEUTRAL", 0.0);
        e2.setAnalyzedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(repository.findAllByOrderByAnalyzedAtDesc()).thenReturn(List.of(e1, e2));

        List<CompanySentiment> result = adapter.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).companySlug()).isEqualTo("new-co");
        assertThat(result.get(1).companySlug()).isEqualTo("old-co");
    }

    @Test
    void save_serializesArticleSentimentsAsJson() {
        CompanySentiment sentiment = buildSentiment("co", "Co", SentimentLabel.NEUTRAL, 0.0);

        adapter.save(sentiment);

        ArgumentCaptor<CompanySentimentEntity> captor = ArgumentCaptor.forClass(CompanySentimentEntity.class);
        verify(repository).save(captor.capture());

        String json = captor.getValue().getArticleSentimentsJson();
        assertThat(json).isNotNull();
        assertThat(json).isEqualTo("[]");
    }

    private CompanySentiment buildSentiment(String slug, String name,
                                             SentimentLabel label, double score) {
        return new CompanySentiment(slug, name, label, score, 10, 5, 2, 2, 1,
                "Risk summary", List.of(), Instant.now());
    }

    private CompanySentimentEntity buildEntity(String slug, String name,
                                                String sentiment, double score) {
        CompanySentimentEntity entity = new CompanySentimentEntity();
        entity.setCompanySlug(slug);
        entity.setCompanyName(name);
        entity.setOverallSentiment(sentiment);
        entity.setSentimentScore(score);
        entity.setTotalArticles(10);
        entity.setPositiveCount(5);
        entity.setNegativeCount(2);
        entity.setMixedCount(2);
        entity.setNeutralCount(1);
        entity.setRiskSummary("Risk summary");
        entity.setArticleSentimentsJson("[]");
        entity.setAnalyzedAt(Instant.now());
        return entity;
    }
}
