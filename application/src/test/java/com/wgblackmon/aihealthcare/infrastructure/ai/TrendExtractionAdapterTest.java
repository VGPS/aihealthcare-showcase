package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.ExtractedTrend;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrendExtractionAdapter}.
 *
 * <p>Tests prompt building and response parsing logic. The ChatClient is mocked
 * for LLM call tests.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-29
 * @updated 2026-07-29
 */
class TrendExtractionAdapterTest {

    private PromptLoaderService promptLoaderService;
    private TrendExtractionAdapter adapter;

    @BeforeEach
    void setUp() {
        promptLoaderService = mock(PromptLoaderService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);
        adapter = new TrendExtractionAdapter(builder, promptLoaderService);
    }

    @Test
    void buildPromptSubstitutesPlaceholders() {
        when(promptLoaderService.load("trend-extract.txt"))
                .thenReturn("Count: {articleCount} Max: {maxTopics}\n{numberedTitles}");

        List<String> titles = List.of("FDA clears AI radiology tool", "New ambient AI partnership");
        String prompt = adapter.buildPrompt(titles, 15);

        assertThat(prompt).contains("Count: 2");
        assertThat(prompt).contains("Max: 15");
        assertThat(prompt).contains("1. FDA clears AI radiology tool");
        assertThat(prompt).contains("2. New ambient AI partnership");
    }

    @Test
    void parseResponseExtractsTrends() {
        String response = """
                TREND:
                LABEL: FDA AI Clearances
                DESCRIPTION: Rising number of FDA clearances for AI diagnostic tools
                ARTICLES: 1, 3, 7

                TREND:
                LABEL: Ambient Clinical AI
                DESCRIPTION: Companies launching ambient documentation products
                ARTICLES: 2, 5
                """;

        List<ExtractedTrend> trends = adapter.parseResponse(response, 10);

        assertThat(trends).hasSize(2);

        assertThat(trends.get(0).label()).isEqualTo("FDA AI Clearances");
        assertThat(trends.get(0).description()).contains("FDA clearances");
        assertThat(trends.get(0).articleIndices()).containsExactly(0, 2, 6); // 1-based → 0-based

        assertThat(trends.get(1).label()).isEqualTo("Ambient Clinical AI");
        assertThat(trends.get(1).articleIndices()).containsExactly(1, 4);
    }

    @Test
    void parseResponseHandlesEmptyResponse() {
        List<ExtractedTrend> trends = adapter.parseResponse("", 10);
        assertThat(trends).isEmpty();
    }

    @Test
    void parseResponseSkipsOutOfRangeIndices() {
        String response = """
                TREND:
                LABEL: Test Theme
                DESCRIPTION: A test theme
                ARTICLES: 1, 50, 100
                """;

        List<ExtractedTrend> trends = adapter.parseResponse(response, 5);

        assertThat(trends).hasSize(1);
        assertThat(trends.get(0).articleIndices()).containsExactly(0); // only 1 is valid (→ index 0)
    }

    @Test
    void parseResponseHandlesMalformedLines() {
        String response = """
                TREND:
                LABEL: Valid Theme
                DESCRIPTION: A valid theme
                ARTICLES: abc, 2, xyz

                TREND:
                LABEL:
                DESCRIPTION: Missing label — should be skipped
                ARTICLES: 1
                """;

        List<ExtractedTrend> trends = adapter.parseResponse(response, 5);

        assertThat(trends).hasSize(1);
        assertThat(trends.get(0).label()).isEqualTo("Valid Theme");
        assertThat(trends.get(0).articleIndices()).containsExactly(1); // only "2" parsed
    }

    @Test
    void extractTopicsReturnsEmptyForNullInput() {
        List<ExtractedTrend> result = adapter.extractTopics(null, 10);
        assertThat(result).isEmpty();
    }

    @Test
    void extractTopicsReturnsEmptyForEmptyInput() {
        List<ExtractedTrend> result = adapter.extractTopics(List.of(), 10);
        assertThat(result).isEmpty();
    }
}
