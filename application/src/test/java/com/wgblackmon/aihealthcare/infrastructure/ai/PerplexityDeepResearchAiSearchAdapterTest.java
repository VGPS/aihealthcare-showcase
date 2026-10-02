package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity.PerplexityChatCompletionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PerplexityDeepResearchAiSearchAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-02
 * @updated 2026-10-02
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PerplexityDeepResearchAiSearchAdapterTest {

    @Mock private AiSearchResponseParser responseParser;
    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec uriSpec;
    @Mock private RestClient.RequestBodySpec bodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private NewsArticle sampleArticle;

    @BeforeEach
    void setUp() {
        sampleArticle = new NewsArticle(
                "art-1", "AI Billing Conflict Grows", URI.create("https://example.com/1"),
                "Body text here", "General AI Healthcare News", null,
                1L, "Healthcare Dive", "INDUSTRY", 0.7, Instant.now());
    }

    @Test
    void modelName_returnsPerplexityDeep() {
        var adapter = new PerplexityDeepResearchAiSearchAdapter(responseParser, "key", "sonar-deep-research", restClient);
        assertThat(adapter.modelName()).isEqualTo("Perplexity Deep");
    }

    @Test
    void modelId_returnsConfiguredValue() {
        var adapter = new PerplexityDeepResearchAiSearchAdapter(responseParser, "key", "sonar-deep-research", restClient);
        assertThat(adapter.modelId()).isEqualTo("sonar-deep-research");
    }

    @Test
    void isAvailable_noApiKey_returnsFalse() {
        var adapter = new PerplexityDeepResearchAiSearchAdapter(responseParser, "", "sonar-deep-research", restClient);
        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_withApiKey_returnsTrue() {
        var adapter = new PerplexityDeepResearchAiSearchAdapter(responseParser, "real-key", "sonar-deep-research", restClient);
        assertThat(adapter.isAvailable()).isTrue();
    }

    @Test
    void synthesize_noApiKey_returnsFallbackWithoutCallingApi() {
        var adapter = new PerplexityDeepResearchAiSearchAdapter(responseParser, "", "sonar-deep-research", restClient);

        AiSearchSynthesis result = adapter.synthesize("AI billing conflict", List.of(sampleArticle));

        assertThat(result.modelName()).isEqualTo("Perplexity Deep");
        assertThat(result.summary()).contains("not configured");
    }

    @Test
    void synthesize_withArticles_callsChatCompletionsAndReturnsResult() {
        var adapter = new PerplexityDeepResearchAiSearchAdapter(responseParser, "api-key", "sonar-deep-research", restClient);

        PerplexityChatCompletionResponse.Message msg =
                new PerplexityChatCompletionResponse.Message("assistant", "SUMMARY: Deep result\nKEY_FINDINGS:\n- Finding one");
        PerplexityChatCompletionResponse.Choice choice =
                new PerplexityChatCompletionResponse.Choice(0, "stop", msg);
        PerplexityChatCompletionResponse apiResponse =
                new PerplexityChatCompletionResponse("id-1", "sonar-deep-research", List.of(choice), null, null);

        when(responseParser.buildPrompt(anyString(), any())).thenReturn("built prompt");
        when(responseParser.parseResponse(anyString(), anyString()))
                .thenReturn(new AiSearchSynthesis("Perplexity Deep", "Deep result", List.of("Finding one"), Instant.now()));

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        doReturn(bodySpec).when(bodySpec).header(anyString(), anyString());
        doReturn(bodySpec).when(bodySpec).contentType(any());
        doReturn(bodySpec).when(bodySpec).body(any(Map.class));
        doReturn(responseSpec).when(bodySpec).retrieve();
        when(responseSpec.body(PerplexityChatCompletionResponse.class)).thenReturn(apiResponse);

        AiSearchSynthesis result = adapter.synthesize("AI billing conflict", List.of(sampleArticle));

        assertThat(result.modelName()).isEqualTo("Perplexity Deep");
        assertThat(result.summary()).isEqualTo("Deep result");
        verify(responseParser).buildPrompt("AI billing conflict", List.of(sampleArticle));
        verify(responseParser).parseResponse(anyString(), eq("Perplexity Deep"));
    }
}
