package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AwsBedrockAiSearchAdapter}.
 *
 * <p>Verifies prompt building, ChatClient call delegation,
 * response parsing (SUMMARY/KEY_FINDINGS), empty response handling,
 * NO_MATCH gating, and error propagation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-07
 * @updated 2026-07-08
 */
class AwsBedrockAiSearchAdapterTest {

    private PromptLoaderService promptLoaderService;
    private ChatModel chatModel;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;

    private static final String PROMPT_TEMPLATE =
            "Search query: {query}\nRetrieved articles ({articleCount} total):\n{articles}\nSUMMARY and KEY_FINDINGS";

    @BeforeEach
    void setUp() {
        promptLoaderService = mock(PromptLoaderService.class);
        chatModel = mock(ChatModel.class);
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(promptLoaderService.load("ai-search-synthesis.txt")).thenReturn(PROMPT_TEMPLATE);
    }

    private void stubChatClientChain(String response) {
        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(response);
    }

    private NewsArticle sampleArticle(String id, String title) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                "Body text for " + title, "AI Healthcare", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9, Instant.now());
    }

    /**
     * Creates the adapter under test using a mock ChatModel.
     * Uses reflection to inject the mock ChatClient after construction,
     * since the production constructor builds a real ChatClient from the ChatModel.
     */
    private AwsBedrockAiSearchAdapter createAdapter() {
        AwsBedrockAiSearchAdapter adapter =
                new AwsBedrockAiSearchAdapter(chatModel, promptLoaderService);
        // Replace the chatClient field with our mock via reflection
        try {
            java.lang.reflect.Field field = AwsBedrockAiSearchAdapter.class.getDeclaredField("chatClient");
            field.setAccessible(true);
            field.set(adapter, chatClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock ChatClient", e);
        }
        return adapter;
    }

    @Test
    void modelName_returnsAmazonAws() {
        AwsBedrockAiSearchAdapter adapter = createAdapter();

        assertThat(adapter.modelName()).isEqualTo("Amazon/AWS");
    }

    @Test
    void synthesize_withValidResponse_returnsSynthesis() {
        stubChatClientChain(
                "SUMMARY: AI diagnostics are transforming radiology workflows.\nKEY_FINDINGS:\n- Accuracy improved by 15%\n- Adoption rate doubled in 2025");

        AwsBedrockAiSearchAdapter adapter = createAdapter();
        AiSearchSynthesis result = adapter.synthesize("AI diagnostics",
                List.of(sampleArticle("a1", "AI in Radiology")));

        assertThat(result.modelName()).isEqualTo("Amazon/AWS");
        assertThat(result.summary()).isEqualTo("AI diagnostics are transforming radiology workflows.");
        assertThat(result.keyFindings()).hasSize(2);
        assertThat(result.keyFindings().get(0)).isEqualTo("Accuracy improved by 15%");
        assertThat(result.keyFindings().get(1)).isEqualTo("Adoption rate doubled in 2025");
        assertThat(result.generatedAt()).isNotNull();
    }

    @Test
    void synthesize_withEmptyResponse_returnsFallback() {
        stubChatClientChain("");

        AwsBedrockAiSearchAdapter adapter = createAdapter();
        AiSearchSynthesis result = adapter.synthesize("test query",
                List.of(sampleArticle("a1", "Test Article")));

        assertThat(result.modelName()).isEqualTo("Amazon/AWS");
        assertThat(result.summary()).isEqualTo("No synthesis available.");
        assertThat(result.keyFindings()).isEmpty();
    }

    @Test
    void synthesize_withNoMatch_returnsNull() {
        stubChatClientChain("NO_MATCH — articles are not relevant to the query");

        AwsBedrockAiSearchAdapter adapter = createAdapter();
        AiSearchSynthesis result = adapter.synthesize("unrelated topic",
                List.of(sampleArticle("a1", "Test")));

        assertThat(result).isNull();
    }

    @Test
    void synthesize_whenApiCallFails_throwsException() {
        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("Connection refused"));

        AwsBedrockAiSearchAdapter adapter = createAdapter();

        assertThatThrownBy(() -> adapter.synthesize("AI diagnostics",
                List.of(sampleArticle("a1", "Test"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    void synthesize_withNoSummaryLine_usesFullResponseAsSummary() {
        stubChatClientChain("This is a free-form response without the expected format.");

        AwsBedrockAiSearchAdapter adapter = createAdapter();
        AiSearchSynthesis result = adapter.synthesize("test",
                List.of(sampleArticle("a1", "Test")));

        assertThat(result.modelName()).isEqualTo("Amazon/AWS");
        assertThat(result.summary()).contains("free-form response");
        assertThat(result.keyFindings()).isEmpty();
    }
}
