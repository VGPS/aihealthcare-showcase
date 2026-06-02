package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity.PerplexityApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PerplexityAiSearchAdapter}.
 *
 * <p>Verifies prompt building, Perplexity Sonar API call delegation,
 * response parsing (SUMMARY/KEY_FINDINGS), graceful fallback when
 * API key is absent, and error propagation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-06-02
 */
class PerplexityAiSearchAdapterTest {

    private PromptLoaderService promptLoaderService;
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    private RestClient.RequestBodySpec requestBodySpec;
    private RestClient.ResponseSpec responseSpec;

    private static final String PROMPT_TEMPLATE =
            "Search query: {query}\nRetrieved articles ({articleCount} total):\n{articles}\nSUMMARY and KEY_FINDINGS";

    @BeforeEach
    void setUp() {
        promptLoaderService = mock(PromptLoaderService.class);
        restClient = mock(RestClient.class);
        requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(promptLoaderService.load("ai-search-synthesis.txt")).thenReturn(PROMPT_TEMPLATE);
    }

    private void stubRestClientChain(PerplexityApiResponse response) {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), any(String[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(PerplexityApiResponse.class)).thenReturn(response);
    }

    private NewsArticle sampleArticle(String id, String title) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                "Body text for " + title, "AI Healthcare", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9, Instant.now());
    }

    @Test
    void modelName_returnsPerplexity() {
        PerplexityAiSearchAdapter adapter =
                new PerplexityAiSearchAdapter(promptLoaderService, "test-key", restClient);

        assertThat(adapter.modelName()).isEqualTo("Perplexity");
    }

    @Test
    void synthesize_withValidResponse_returnsSynthesis() {
        PerplexityApiResponse.PerplexityMessage message =
                new PerplexityApiResponse.PerplexityMessage(
                        "assistant",
                        "SUMMARY: AI diagnostics are transforming radiology workflows.\nKEY_FINDINGS:\n- Accuracy improved by 15%\n- Adoption rate doubled in 2025");
        PerplexityApiResponse.PerplexityChoice choice =
                new PerplexityApiResponse.PerplexityChoice(message);
        PerplexityApiResponse apiResponse =
                new PerplexityApiResponse("resp-1", List.of(choice), List.of());

        stubRestClientChain(apiResponse);

        PerplexityAiSearchAdapter adapter =
                new PerplexityAiSearchAdapter(promptLoaderService, "test-key", restClient);
        AiSearchSynthesis result = adapter.synthesize("AI diagnostics",
                List.of(sampleArticle("a1", "AI in Radiology")));

        assertThat(result.modelName()).isEqualTo("Perplexity");
        assertThat(result.summary()).isEqualTo("AI diagnostics are transforming radiology workflows.");
        assertThat(result.keyFindings()).hasSize(2);
        assertThat(result.keyFindings().get(0)).isEqualTo("Accuracy improved by 15%");
        assertThat(result.keyFindings().get(1)).isEqualTo("Adoption rate doubled in 2025");
        assertThat(result.generatedAt()).isNotNull();
    }

    @Test
    void synthesize_withEmptyResponse_returnsFallback() {
        PerplexityApiResponse apiResponse =
                new PerplexityApiResponse("resp-2", List.of(), null);

        stubRestClientChain(apiResponse);

        PerplexityAiSearchAdapter adapter =
                new PerplexityAiSearchAdapter(promptLoaderService, "test-key", restClient);
        AiSearchSynthesis result = adapter.synthesize("test query",
                List.of(sampleArticle("a1", "Test Article")));

        assertThat(result.modelName()).isEqualTo("Perplexity");
        assertThat(result.summary()).isEqualTo("No synthesis available.");
        assertThat(result.keyFindings()).isEmpty();
    }

    @Test
    void synthesize_whenApiKeyBlank_returnsFallbackSynthesis() {
        PerplexityAiSearchAdapter adapter =
                new PerplexityAiSearchAdapter(promptLoaderService, "", restClient);
        AiSearchSynthesis result = adapter.synthesize("AI diagnostics",
                List.of(sampleArticle("a1", "Test")));

        assertThat(result.modelName()).isEqualTo("Perplexity");
        assertThat(result.summary()).isEqualTo("Perplexity API key not configured.");
        assertThat(result.keyFindings()).isEmpty();
    }

    @Test
    void synthesize_whenApiCallFails_throwsException() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), any(String[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(PerplexityApiResponse.class))
                .thenThrow(new RuntimeException("Connection refused"));

        PerplexityAiSearchAdapter adapter =
                new PerplexityAiSearchAdapter(promptLoaderService, "test-key", restClient);

        assertThatThrownBy(() -> adapter.synthesize("AI diagnostics",
                List.of(sampleArticle("a1", "Test"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    void synthesize_withNoSummaryLine_usesFullResponseAsSummary() {
        PerplexityApiResponse.PerplexityMessage message =
                new PerplexityApiResponse.PerplexityMessage(
                        "assistant",
                        "This is a free-form response without the expected format.");
        PerplexityApiResponse.PerplexityChoice choice =
                new PerplexityApiResponse.PerplexityChoice(message);
        PerplexityApiResponse apiResponse =
                new PerplexityApiResponse("resp-3", List.of(choice), List.of());

        stubRestClientChain(apiResponse);

        PerplexityAiSearchAdapter adapter =
                new PerplexityAiSearchAdapter(promptLoaderService, "test-key", restClient);
        AiSearchSynthesis result = adapter.synthesize("test",
                List.of(sampleArticle("a1", "Test")));

        assertThat(result.modelName()).isEqualTo("Perplexity");
        assertThat(result.summary()).contains("free-form response");
        assertThat(result.keyFindings()).isEmpty();
    }
}
