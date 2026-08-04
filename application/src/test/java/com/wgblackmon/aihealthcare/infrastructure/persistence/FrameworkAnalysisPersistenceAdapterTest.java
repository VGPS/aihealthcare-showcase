package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.FrameworkDimension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FrameworkAnalysisPersistenceAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
class FrameworkAnalysisPersistenceAdapterTest {

    private FrameworkAnalysisRepository repository;
    private FrameworkAnalysisPersistenceAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        repository = mock(FrameworkAnalysisRepository.class);
        adapter = new FrameworkAnalysisPersistenceAdapter(repository, objectMapper);
    }

    @Test
    void save_persistsEntityCorrectly() {
        FrameworkAnalysis analysis = buildAnalysis("anthropic", "Anthropic");
        when(repository.save(any(FrameworkAnalysisEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        adapter.save(analysis);

        ArgumentCaptor<FrameworkAnalysisEntity> captor = ArgumentCaptor.forClass(FrameworkAnalysisEntity.class);
        verify(repository).save(captor.capture());

        FrameworkAnalysisEntity entity = captor.getValue();
        assertThat(entity.getCompanySlug()).isEqualTo("anthropic");
        assertThat(entity.getCompanyName()).isEqualTo("Anthropic");
        assertThat(entity.getOverallScore()).isEqualTo(7);
        assertThat(entity.getDimensionsJson()).contains("Technical Maturity");
        assertThat(entity.getStrengthsJson()).contains("Good docs");
    }

    @Test
    void findBySlug_returnsAnalysis() throws Exception {
        FrameworkAnalysisEntity entity = buildEntity("anthropic", "Anthropic");
        when(repository.findById("anthropic")).thenReturn(Optional.of(entity));

        Optional<FrameworkAnalysis> result = adapter.findBySlug("anthropic");

        assertThat(result).isPresent();
        assertThat(result.get().companySlug()).isEqualTo("anthropic");
        assertThat(result.get().dimensions()).hasSize(1);
        assertThat(result.get().strengths()).containsExactly("Good docs");
    }

    @Test
    void findBySlug_returnsEmptyWhenNotFound() {
        when(repository.findById("unknown")).thenReturn(Optional.empty());

        Optional<FrameworkAnalysis> result = adapter.findBySlug("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_returnsAllOrderedByScore() throws Exception {
        FrameworkAnalysisEntity entity1 = buildEntity("openai", "OpenAI");
        FrameworkAnalysisEntity entity2 = buildEntity("anthropic", "Anthropic");
        when(repository.findAllByOrderByOverallScoreDesc()).thenReturn(List.of(entity1, entity2));

        List<FrameworkAnalysis> results = adapter.findAll();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).companySlug()).isEqualTo("openai");
        assertThat(results.get(1).companySlug()).isEqualTo("anthropic");
    }

    @Test
    void findBySlug_handlesEmptyJsonGracefully() {
        FrameworkAnalysisEntity entity = new FrameworkAnalysisEntity();
        entity.setCompanySlug("test");
        entity.setCompanyName("Test");
        entity.setOverallAssessment("Assessment");
        entity.setDimensionsJson(null);
        entity.setStrengthsJson(null);
        entity.setWeaknessesJson(null);
        entity.setRecentDevelopmentsJson(null);
        entity.setOverallScore(5);
        entity.setArticleCount(3);
        entity.setAnalyzedAt(Instant.now());

        when(repository.findById("test")).thenReturn(Optional.of(entity));

        Optional<FrameworkAnalysis> result = adapter.findBySlug("test");

        assertThat(result).isPresent();
        assertThat(result.get().dimensions()).isEmpty();
        assertThat(result.get().strengths()).isEmpty();
    }

    private FrameworkAnalysis buildAnalysis(String slug, String name) {
        return new FrameworkAnalysis(
                slug, name, "Overall assessment",
                List.of(new FrameworkDimension("Technical Maturity", 7, "Strong APIs")),
                List.of("Good docs"), List.of("Limited scope"),
                List.of("New feature"), 7, 10, Instant.now());
    }

    private FrameworkAnalysisEntity buildEntity(String slug, String name) throws Exception {
        FrameworkAnalysisEntity entity = new FrameworkAnalysisEntity();
        entity.setCompanySlug(slug);
        entity.setCompanyName(name);
        entity.setOverallAssessment("Overall assessment");
        entity.setDimensionsJson(objectMapper.writeValueAsString(
                List.of(new FrameworkDimension("Technical Maturity", 7, "Strong APIs"))));
        entity.setStrengthsJson(objectMapper.writeValueAsString(List.of("Good docs")));
        entity.setWeaknessesJson(objectMapper.writeValueAsString(List.of("Limited scope")));
        entity.setRecentDevelopmentsJson(objectMapper.writeValueAsString(List.of("New feature")));
        entity.setOverallScore(7);
        entity.setArticleCount(10);
        entity.setAnalyzedAt(Instant.now());
        return entity;
    }
}
