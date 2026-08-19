package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ArticleBodyFormattingAdapter}.
 *
 * <p>Tests early-return edge cases, exception handling, and prompt template
 * loading without making real LLM calls. A test subclass ({@link TestAdapter})
 * passes a null-returning {@link org.springframework.ai.chat.model.ChatModel}
 * to {@link ChatClient.Builder} so the fluent chain throws a
 * {@code NullPointerException} caught by the adapter's try/catch block — verifying
 * the fail-open behaviour that returns an empty string.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArticleBodyFormattingAdapterTest {

    @Mock
    private PromptLoaderService promptLoaderService;

    private ArticleBodyFormattingAdapter adapter;

    @BeforeEach
    void setUp() {
        when(promptLoaderService.load("article-format.txt"))
                .thenReturn("Title: {title}\n\nBody:\n{bodyText}");
        adapter = new TestAdapter(promptLoaderService);
    }

    @Test
    void formatWithEntityBolding_nullBody_returnsEmpty() {
        String result = adapter.formatWithEntityBolding("Some Title", null);
        assertThat(result).isEmpty();
    }

    @Test
    void formatWithEntityBolding_blankBody_returnsEmpty() {
        String result = adapter.formatWithEntityBolding("Some Title", "   ");
        assertThat(result).isEmpty();
    }

    @Test
    void formatWithEntityBolding_validBody_loadsPromptFile() {
        // The null ChatModel causes NPE in the chain — adapter catches it and returns ""
        adapter.formatWithEntityBolding("FDA Clears AI Device", "New AI device approved today.");

        verify(promptLoaderService).load("article-format.txt");
    }

    @Test
    void formatWithEntityBolding_llmFails_returnsEmpty() {
        // The null ChatModel causes NPE on .content() — adapter's try/catch returns ""
        String result = adapter.formatWithEntityBolding("Title", "Body text with real content here.");
        assertThat(result).isEmpty();
    }

    @Test
    void formatWithEntityBolding_nullTitle_stillCallsPromptLoader() {
        // Null title is substituted as "" per adapter implementation
        adapter.formatWithEntityBolding(null, "Some body text.");

        verify(promptLoaderService).load("article-format.txt");
    }

    /**
     * Test subclass that avoids real LLM calls by using a null-returning ChatModel.
     * The fluent chain {@code chatClient.prompt(p).call().content()} throws
     * NullPointerException, which the adapter's try/catch block catches and
     * converts to an empty-string result.
     */
    private static class TestAdapter extends ArticleBodyFormattingAdapter {

        TestAdapter(PromptLoaderService promptLoaderService) {
            super(ChatClient.builder(new org.springframework.ai.chat.model.ChatModel() {
                @Override
                public org.springframework.ai.chat.model.ChatResponse call(
                        org.springframework.ai.chat.prompt.Prompt prompt) {
                    return null;
                }
            }), promptLoaderService);
        }
    }
}
