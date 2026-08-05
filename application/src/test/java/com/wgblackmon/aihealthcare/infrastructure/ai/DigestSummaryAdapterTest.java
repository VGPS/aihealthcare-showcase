package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DigestSummaryAdapter}.
 *
 * <p>Tests prompt construction and response handling. The ChatClient is mocked
 * so no real LLM calls are made.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-05
 * @updated 2026-08-05
 */
class DigestSummaryAdapterTest {

    private PromptLoaderService promptLoaderService;
    private DigestSummaryAdapter adapter;

    private static NewsArticle article(String id, String title, String body) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                body, "AI", null, null, "TestSource", null, 0.5, null
        );
    }

    @BeforeEach
    void setUp() {
        promptLoaderService = mock(PromptLoaderService.class);
        when(promptLoaderService.load("digest-summary.txt"))
                .thenReturn("Summarize {articleCount} articles:\n{numberedArticleList}");
    }

    @Test
    void buildPrompt_includesArticleCount() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        adapter = new DigestSummaryAdapter(builder, promptLoaderService);

        List<NewsArticle> articles = List.of(
                article("a-001", "First Article", "Body one"),
                article("a-002", "Second Article", "Body two")
        );

        String prompt = adapter.buildPrompt(articles);
        assertThat(prompt).contains("Summarize 2 articles:");
    }

    @Test
    void buildPrompt_includesNumberedArticleList() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        adapter = new DigestSummaryAdapter(builder, promptLoaderService);

        List<NewsArticle> articles = List.of(
                article("a-001", "First Article", "Body one"),
                article("a-002", "Second Article", "Body two")
        );

        String prompt = adapter.buildPrompt(articles);
        assertThat(prompt).contains("[1] First Article");
        assertThat(prompt).contains("[2] Second Article");
    }

    @Test
    void buildPrompt_truncatesLongBodyText() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        adapter = new DigestSummaryAdapter(builder, promptLoaderService);

        String longBody = "A".repeat(500);
        List<NewsArticle> articles = List.of(article("a-001", "Long Article", longBody));

        String prompt = adapter.buildPrompt(articles);
        assertThat(prompt).contains("...");
        assertThat(prompt.length()).isLessThan(600);
    }

    @Test
    void generateDigestSummary_returnsEmptyForNullInput() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        adapter = new DigestSummaryAdapter(builder, promptLoaderService);

        String result = adapter.generateDigestSummary(null);
        assertThat(result).isEmpty();
    }

    @Test
    void generateDigestSummary_returnsEmptyForEmptyList() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        adapter = new DigestSummaryAdapter(builder, promptLoaderService);

        String result = adapter.generateDigestSummary(List.of());
        assertThat(result).isEmpty();
    }
}
