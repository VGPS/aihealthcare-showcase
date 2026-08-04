package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.FrameworkCompany;
import com.wgblackmon.aihealthcare.domain.model.FrameworkDimension;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrameworkAnalysisPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrameworkLlmPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FrameworkAnalysisService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
class FrameworkAnalysisServiceTest {

    private ArticleIngestionPort articleIngestionPort;
    private FrameworkLlmPort frameworkLlmPort;
    private FrameworkAnalysisPort frameworkAnalysisPort;
    private FrameworkAnalysisService service;

    @BeforeEach
    void setUp() {
        articleIngestionPort = mock(ArticleIngestionPort.class);
        frameworkLlmPort = mock(FrameworkLlmPort.class);
        frameworkAnalysisPort = mock(FrameworkAnalysisPort.class);

        List<FrameworkCompany> companies = List.of(
                new FrameworkCompany("anthropic", "Anthropic", "https://www.anthropic.com",
                        List.of("Anthropic Healthcare")),
                new FrameworkCompany("openai", "OpenAI", "https://openai.com",
                        List.of("OpenAI Healthcare"))
        );

        service = new FrameworkAnalysisService(
                companies, articleIngestionPort, frameworkLlmPort, frameworkAnalysisPort);
    }

    @Test
    void analyzeAll_analyzesCompaniesWithEnoughArticles() {
        List<NewsArticle> articles = buildArticles(5, "Anthropic Healthcare");
        when(articleIngestionPort.fetchAllByTopic("Anthropic Healthcare")).thenReturn(articles);
        when(articleIngestionPort.fetchAllByTopic("OpenAI Healthcare")).thenReturn(buildArticles(5, "OpenAI Healthcare"));

        FrameworkAnalysis anthropicAnalysis = buildAnalysis("anthropic", "Anthropic");
        FrameworkAnalysis openaiAnalysis = buildAnalysis("openai", "OpenAI");
        when(frameworkLlmPort.analyze(eq("anthropic"), eq("Anthropic"), anyList())).thenReturn(anthropicAnalysis);
        when(frameworkLlmPort.analyze(eq("openai"), eq("OpenAI"), anyList())).thenReturn(openaiAnalysis);

        List<FrameworkAnalysis> results = service.analyzeAll();

        assertThat(results).hasSize(2);
        verify(frameworkAnalysisPort).save(anthropicAnalysis);
        verify(frameworkAnalysisPort).save(openaiAnalysis);
    }

    @Test
    void analyzeAll_skipsCompaniesWithTooFewArticles() {
        when(articleIngestionPort.fetchAllByTopic("Anthropic Healthcare")).thenReturn(buildArticles(2, "Anthropic Healthcare"));
        when(articleIngestionPort.fetchAllByTopic("OpenAI Healthcare")).thenReturn(buildArticles(5, "OpenAI Healthcare"));

        FrameworkAnalysis openaiAnalysis = buildAnalysis("openai", "OpenAI");
        when(frameworkLlmPort.analyze(eq("openai"), eq("OpenAI"), anyList())).thenReturn(openaiAnalysis);

        List<FrameworkAnalysis> results = service.analyzeAll();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).companySlug()).isEqualTo("openai");
        verify(frameworkLlmPort, never()).analyze(eq("anthropic"), anyString(), anyList());
    }

    @Test
    void analyzeAll_skipsWhenLlmReturnsNull() {
        when(articleIngestionPort.fetchAllByTopic("Anthropic Healthcare")).thenReturn(buildArticles(5, "Anthropic Healthcare"));
        when(articleIngestionPort.fetchAllByTopic("OpenAI Healthcare")).thenReturn(buildArticles(5, "OpenAI Healthcare"));

        when(frameworkLlmPort.analyze(eq("anthropic"), eq("Anthropic"), anyList())).thenReturn(null);
        when(frameworkLlmPort.analyze(eq("openai"), eq("OpenAI"), anyList())).thenReturn(buildAnalysis("openai", "OpenAI"));

        List<FrameworkAnalysis> results = service.analyzeAll();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).companySlug()).isEqualTo("openai");
        verify(frameworkAnalysisPort).save(results.get(0));
    }

    @Test
    void analyzeAll_deduplicatesArticlesByIdAcrossTopics() {
        FrameworkAnalysisService singleCompanyService = new FrameworkAnalysisService(
                List.of(new FrameworkCompany("test", "Test", null, List.of("Topic A", "Topic B"))),
                articleIngestionPort, frameworkLlmPort, frameworkAnalysisPort);

        List<NewsArticle> topicAArticles = buildArticles(3, "Topic A");
        List<NewsArticle> topicBArticles = new ArrayList<>(topicAArticles.subList(0, 2));
        topicBArticles.add(buildArticle("unique-4", "Topic B"));

        when(articleIngestionPort.fetchAllByTopic("Topic A")).thenReturn(topicAArticles);
        when(articleIngestionPort.fetchAllByTopic("Topic B")).thenReturn(topicBArticles);

        FrameworkAnalysis analysis = buildAnalysis("test", "Test");
        ArgumentCaptor<List<NewsArticle>> captor = ArgumentCaptor.forClass(List.class);
        when(frameworkLlmPort.analyze(eq("test"), eq("Test"), captor.capture())).thenReturn(analysis);

        singleCompanyService.analyzeAll();

        assertThat(captor.getValue()).hasSize(4);
    }

    @Test
    void getBySlug_delegatesToPort() {
        FrameworkAnalysis analysis = buildAnalysis("anthropic", "Anthropic");
        when(frameworkAnalysisPort.findBySlug("anthropic")).thenReturn(Optional.of(analysis));

        Optional<FrameworkAnalysis> result = service.getBySlug("anthropic");

        assertThat(result).isPresent();
        assertThat(result.get().companySlug()).isEqualTo("anthropic");
    }

    @Test
    void getBySlug_returnsEmptyWhenNotFound() {
        when(frameworkAnalysisPort.findBySlug("unknown")).thenReturn(Optional.empty());

        Optional<FrameworkAnalysis> result = service.getBySlug("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void getAll_delegatesToPort() {
        when(frameworkAnalysisPort.findAll()).thenReturn(List.of(buildAnalysis("a", "A")));

        List<FrameworkAnalysis> result = service.getAll();

        assertThat(result).hasSize(1);
    }

    private FrameworkAnalysis buildAnalysis(String slug, String name) {
        return new FrameworkAnalysis(
                slug, name, "Overall assessment text",
                List.of(new FrameworkDimension("Technical Maturity", 7, "Good APIs")),
                List.of("Strong docs"), List.of("Limited coverage"),
                List.of("New release"), 7, 5, Instant.now());
    }

    private List<NewsArticle> buildArticles(int count, String topic) {
        List<NewsArticle> articles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            articles.add(buildArticle("article-" + topic.hashCode() + "-" + i, topic));
        }
        return articles;
    }

    private NewsArticle buildArticle(String id, String topic) {
        return new NewsArticle(id, "Title " + id,
                URI.create("https://example.com/" + id),
                "Body text for " + id, topic, null, null,
                null, null, 0.5, null);
    }
}
