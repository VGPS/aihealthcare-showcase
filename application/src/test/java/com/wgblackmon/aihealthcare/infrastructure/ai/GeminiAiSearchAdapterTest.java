package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Unit tests for {@link GeminiAiSearchAdapter}.
 *
 * <p>Verifies prompt building, Gemini REST API call delegation,
 * response parsing (SUMMARY/KEY_FINDINGS), graceful fallback when
 * API key is absent, and error propagation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
class GeminiAiSearchAdapterTest {

    private AiSearchResponseParser responseParser;
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    private RestClient.RequestBodySpec requestBodySpec;
    private RestClient.ResponseSpec responseSpec;

    private static final String PROMPT_TEMPLATE =
            "Search query: {query}\nRetrieved articles ({articleCount} total):\n{articles}\nSUMMARY and KEY_FINDINGS";

    @BeforeEach
    void setUp() {
        PromptLoaderService promptLoaderService = mock(PromptLoaderService.class);
        when(promptLoaderService.load("ai-search-synthesis.txt")).thenReturn(PROMPT_TEMPLATE);
        responseParser = new AiSearchResponseParser(promptLoaderService);

        restClient = mock(RestClient.class);
        requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
    }

    private void stubRestClientChain(GeminiApiResponse response) {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(GeminiApiResponse.class)).thenReturn(response);
    }

    private NewsArticle sampleArticle(String id, String title) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                "Body text for " + title, "AI Healthcare", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9, Instant.now());
    }

    @Test
    void modelName_returnsGemini() {
        GeminiAiSearchAdapter adapter =
                new GeminiAiSearchAdapter(responseParser, "test-key", "gemini-3.5-flash", restClient);

        assertThat(adapter.modelName()).isEqualTo("Gemini");
    }

    @Test
    void synthesize_withValidResponse_returnsSynthesis() {
        GeminiApiResponse.GeminiPart part = new GeminiApiResponse.GeminiPart(
                "SUMMARY: AI diagnostics are transforming radiology workflows.\nKEY_FINDINGS:\n- Accuracy improved by 15%\n- Adoption rate doubled in 2025");
        GeminiApiResponse.GeminiContent content = new GeminiApiResponse.GeminiContent(List.of(part));
        GeminiApiResponse.GeminiCandidate candidate = new GeminiApiResponse.GeminiCandidate(content);
        GeminiApiResponse apiResponse = new GeminiApiResponse(List.of(candidate));

        stubRestClientChain(apiResponse);

        GeminiAiSearchAdapter adapter =
                new GeminiAiSearchAdapter(responseParser, "test-key", "gemini-3.5-flash", restClient);
        AiSearchSynthesis result = adapter.synthesize("AI diagnostics",
                List.of(sampleArticle("a1", "AI in Radiology")));

        assertThat(result.modelName()).isEqualTo("Gemini");
        assertThat(result.summary()).isEqualTo("AI diagnostics are transforming radiology workflows.");
        assertThat(result.keyFindings()).hasSize(2);
        assertThat(result.keyFindings().get(0)).isEqualTo("Accuracy improved by 15%");
        assertThat(result.keyFindings().get(1)).isEqualTo("Adoption rate doubled in 2025");
        assertThat(result.generatedAt()).isNotNull();
    }

    @Test
    void synthesize_withEmptyResponse_returnsFallback() {
        GeminiApiResponse apiResponse = new GeminiApiResponse(List.of());

        stubRestClientChain(apiResponse);

        GeminiAiSearchAdapter adapter =
                new GeminiAiSearchAdapter(responseParser, "test-key", "gemini-3.5-flash", restClient);
        AiSearchSynthesis result = adapter.synthesize("test query",
                List.of(sampleArticle("a1", "Test Article")));

        assertThat(result.modelName()).isEqualTo("Gemini");
        assertThat(result.summary()).isEqualTo("No synthesis available.");
        assertThat(result.keyFindings()).isEmpty();
    }

    @Test
    void synthesize_whenApiKeyBlank_returnsFallbackSynthesis() {
        GeminiAiSearchAdapter adapter =
                new GeminiAiSearchAdapter(responseParser, "", "gemini-3.5-flash", restClient);
        AiSearchSynthesis result = adapter.synthesize("AI diagnostics",
                List.of(sampleArticle("a1", "Test")));

        assertThat(result.modelName()).isEqualTo("Gemini");
        assertThat(result.summary()).isEqualTo("Gemini API key not configured.");
        assertThat(result.keyFindings()).isEmpty();
    }

    @Test
    void synthesize_whenApiCallFails_throwsException() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(GeminiApiResponse.class))
                .thenThrow(new RuntimeException("Connection refused"));

        GeminiAiSearchAdapter adapter =
                new GeminiAiSearchAdapter(responseParser, "test-key", "gemini-3.5-flash", restClient);

        assertThatThrownBy(() -> adapter.synthesize("AI diagnostics",
                List.of(sampleArticle("a1", "Test"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    void synthesize_withNoSummaryLine_usesFullResponseAsSummary() {
        GeminiApiResponse.GeminiPart part = new GeminiApiResponse.GeminiPart(
                "This is a free-form response without the expected format.");
        GeminiApiResponse.GeminiContent content = new GeminiApiResponse.GeminiContent(List.of(part));
        GeminiApiResponse.GeminiCandidate candidate = new GeminiApiResponse.GeminiCandidate(content);
        GeminiApiResponse apiResponse = new GeminiApiResponse(List.of(candidate));

        stubRestClientChain(apiResponse);

        GeminiAiSearchAdapter adapter =
                new GeminiAiSearchAdapter(responseParser, "test-key", "gemini-3.5-flash", restClient);
        AiSearchSynthesis result = adapter.synthesize("test",
                List.of(sampleArticle("a1", "Test")));

        assertThat(result.modelName()).isEqualTo("Gemini");
        assertThat(result.summary()).contains("free-form response");
        assertThat(result.keyFindings()).isEmpty();
    }
}
