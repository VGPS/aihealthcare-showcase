package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiSearchService}.
 *
 * <p>Verifies vector search delegation, multi-port synthesis fan-out,
 * empty/null query handling, and graceful degradation when a model fails.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-06-02
 */
class AiSearchServiceTest {

    private ArticleSearchPort vectorSearch;
    private AiSearchPort claudePort;
    private AiSearchPort gptPort;
    private AiSearchPort perplexityPort;
    private AiSearchService service;

    @BeforeEach
    void setUp() {
        vectorSearch = mock(ArticleSearchPort.class);
        claudePort = mock(AiSearchPort.class);
        gptPort = mock(AiSearchPort.class);
        perplexityPort = mock(AiSearchPort.class);

        when(claudePort.modelName()).thenReturn("Claude");
        when(gptPort.modelName()).thenReturn("GPT");
        when(perplexityPort.modelName()).thenReturn("Perplexity");

        service = new AiSearchService(vectorSearch, List.of(claudePort, gptPort, perplexityPort));
    }

    private NewsArticle sampleArticle(String id, String title) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                "Body text for " + title, "AI Healthcare", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9, Instant.now());
    }

    @Test
    void search_withResults_returnsSynthesesFromAllPorts() {
        NewsArticle a1 = sampleArticle("a1", "AI in Radiology");
        NewsArticle a2 = sampleArticle("a2", "ML in Pathology");
        when(vectorSearch.findSimilar(eq("AI diagnostics"), eq(10))).thenReturn(List.of(a1, a2));

        AiSearchSynthesis claudeSynthesis = new AiSearchSynthesis(
                "Claude", "Claude summary", List.of("Finding 1"), Instant.now());
        AiSearchSynthesis gptSynthesis = new AiSearchSynthesis(
                "GPT", "GPT summary", List.of("Finding 2"), Instant.now());
        AiSearchSynthesis perplexitySynthesis = new AiSearchSynthesis(
                "Perplexity", "Perplexity summary", List.of("Finding 3"), Instant.now());

        when(claudePort.synthesize(eq("AI diagnostics"), eq(List.of(a1, a2)))).thenReturn(claudeSynthesis);
        when(gptPort.synthesize(eq("AI diagnostics"), eq(List.of(a1, a2)))).thenReturn(gptSynthesis);
        when(perplexityPort.synthesize(eq("AI diagnostics"), eq(List.of(a1, a2)))).thenReturn(perplexitySynthesis);

        AiSearchResult result = service.search("AI diagnostics", 10);

        assertThat(result.query()).isEqualTo("AI diagnostics");
        assertThat(result.articles()).hasSize(2);
        assertThat(result.syntheses()).hasSize(3);
        assertThat(result.syntheses().get(0).modelName()).isEqualTo("Claude");
        assertThat(result.syntheses().get(1).modelName()).isEqualTo("GPT");
        assertThat(result.syntheses().get(2).modelName()).isEqualTo("Perplexity");
        assertThat(result.searchId()).isNotBlank();
        assertThat(result.searchedAt()).isNotNull();
    }

    @Test
    void search_withEmptyVectorResults_returnsEmptySyntheses() {
        when(vectorSearch.findSimilar(anyString(), anyInt())).thenReturn(List.of());

        AiSearchResult result = service.search("obscure topic", 10);

        assertThat(result.articles()).isEmpty();
        assertThat(result.syntheses()).isEmpty();
        verify(claudePort, never()).synthesize(anyString(), anyList());
        verify(gptPort, never()).synthesize(anyString(), anyList());
        verify(perplexityPort, never()).synthesize(anyString(), anyList());
    }

    @Test
    void search_withNullQuery_returnsEmptyResult() {
        AiSearchResult result = service.search(null, 10);

        assertThat(result.query()).isEmpty();
        assertThat(result.articles()).isEmpty();
        assertThat(result.syntheses()).isEmpty();
        verify(vectorSearch, never()).findSimilar(anyString(), anyInt());
    }

    @Test
    void search_withBlankQuery_returnsEmptyResult() {
        AiSearchResult result = service.search("   ", 10);

        assertThat(result.query()).isEmpty();
        assertThat(result.articles()).isEmpty();
        assertThat(result.syntheses()).isEmpty();
        verify(vectorSearch, never()).findSimilar(anyString(), anyInt());
    }

    @Test
    void search_whenOneModelFails_returnsPartialSyntheses() {
        NewsArticle a1 = sampleArticle("a1", "AI in Surgery");
        when(vectorSearch.findSimilar(eq("surgery AI"), eq(5))).thenReturn(List.of(a1));

        AiSearchSynthesis claudeSynthesis = new AiSearchSynthesis(
                "Claude", "Claude summary", List.of("Finding"), Instant.now());
        AiSearchSynthesis perplexitySynthesis = new AiSearchSynthesis(
                "Perplexity", "Perplexity summary", List.of("Finding"), Instant.now());
        when(claudePort.synthesize(eq("surgery AI"), eq(List.of(a1)))).thenReturn(claudeSynthesis);
        when(gptPort.synthesize(eq("surgery AI"), eq(List.of(a1)))).thenThrow(new RuntimeException("API error"));
        when(perplexityPort.synthesize(eq("surgery AI"), eq(List.of(a1)))).thenReturn(perplexitySynthesis);

        AiSearchResult result = service.search("surgery AI", 5);

        assertThat(result.articles()).hasSize(1);
        assertThat(result.syntheses()).hasSize(2);
        assertThat(result.syntheses().get(0).modelName()).isEqualTo("Claude");
        assertThat(result.syntheses().get(1).modelName()).isEqualTo("Perplexity");
    }

    // Helper to avoid Mockito import issues
    private static <T> List<T> anyList() {
        return org.mockito.ArgumentMatchers.anyList();
    }
}
