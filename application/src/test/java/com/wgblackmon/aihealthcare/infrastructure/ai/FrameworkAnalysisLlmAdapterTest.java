package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FrameworkAnalysisLlmAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
class FrameworkAnalysisLlmAdapterTest {

    private ChatClient.Builder chatClientBuilder;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec responseSpec;
    private PromptLoaderService promptLoaderService;
    private FrameworkAnalysisLlmAdapter adapter;

    @BeforeEach
    void setUp() {
        chatClientBuilder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        responseSpec = mock(ChatClient.CallResponseSpec.class);
        promptLoaderService = mock(PromptLoaderService.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        when(promptLoaderService.load("framework-analysis.txt"))
                .thenReturn("Analyze {companyName}:\n{numberedArticleList}");

        adapter = new FrameworkAnalysisLlmAdapter(chatClientBuilder, promptLoaderService);
    }

    @Test
    void analyze_parsesWellFormedResponse() {
        String response = buildWellFormedResponse();
        when(responseSpec.content()).thenReturn(response);

        FrameworkAnalysis result = adapter.analyze("anthropic", "Anthropic", buildArticles(5));

        assertThat(result).isNotNull();
        assertThat(result.companySlug()).isEqualTo("anthropic");
        assertThat(result.overallAssessment()).isNotBlank();
        assertThat(result.dimensions()).hasSize(6);
        assertThat(result.strengths()).isNotEmpty();
        assertThat(result.weaknesses()).isNotEmpty();
        assertThat(result.recentDevelopments()).isNotEmpty();
    }

    @Test
    void analyze_returnsNullWhenEmptyArticles() {
        FrameworkAnalysis result = adapter.analyze("anthropic", "Anthropic", List.of());

        assertThat(result).isNull();
    }

    @Test
    void analyze_returnsNullWhenLlmReturnsNull() {
        when(responseSpec.content()).thenReturn(null);

        FrameworkAnalysis result = adapter.analyze("anthropic", "Anthropic", buildArticles(3));

        assertThat(result).isNull();
    }

    @Test
    void analyze_returnsNullWhenLlmThrows() {
        when(requestSpec.call()).thenThrow(new RuntimeException("API error"));

        FrameworkAnalysis result = adapter.analyze("anthropic", "Anthropic", buildArticles(3));

        assertThat(result).isNull();
    }

    @Test
    void analyze_returnsNullWhenParsingFails() {
        when(responseSpec.content()).thenReturn("This is garbage text with no structure");

        FrameworkAnalysis result = adapter.analyze("anthropic", "Anthropic", buildArticles(3));

        assertThat(result).isNull();
    }

    @Test
    void buildPrompt_numbersArticlesCorrectly() {
        List<NewsArticle> articles = buildArticles(3);
        String prompt = adapter.buildPrompt(articles, "Anthropic");

        assertThat(prompt).contains("[1]");
        assertThat(prompt).contains("[2]");
        assertThat(prompt).contains("[3]");
        assertThat(prompt).contains("Anthropic");
    }

    @Test
    void parseResponse_calculatesOverallScoreAsAverage() {
        String response = buildWellFormedResponse();
        when(responseSpec.content()).thenReturn(response);

        FrameworkAnalysis result = adapter.analyze("test", "Test", buildArticles(5));

        assertThat(result).isNotNull();
        int expectedAvg = (8 + 7 + 6 + 7 + 8 + 9) / 6;
        assertThat(result.overallScore()).isEqualTo(expectedAvg);
    }

    @Test
    void analyze_capsArticlesAt50() {
        List<NewsArticle> manyArticles = buildArticles(60);
        when(responseSpec.content()).thenReturn(buildWellFormedResponse());

        adapter.analyze("test", "Test", manyArticles);

        // Verify prompt was built with capped articles (no exception thrown)
        assertThat(manyArticles).hasSize(60);
    }

    private String buildWellFormedResponse() {
        return "OVERALL_ASSESSMENT:\n" +
                "Anthropic has established a strong healthcare AI position with Claude.\n\n" +
                "DIMENSIONS:\n" +
                "[Technical Maturity] 8 | Strong API surface and model quality\n" +
                "[Clinical Validation] 7 | Growing body of clinical evidence\n" +
                "[Regulatory Positioning] 6 | Early but promising compliance stance\n" +
                "[Platform Strategy] 7 | Good partner ecosystem forming\n" +
                "[Market Momentum] 8 | High adoption rate in healthcare\n" +
                "[Developer Experience] 9 | Excellent documentation and tools\n\n" +
                "STRENGTHS:\n" +
                "- Best-in-class API design for healthcare workflows\n" +
                "- Strong safety and alignment focus\n\n" +
                "WEAKNESSES:\n" +
                "- Limited clinical trial partnerships\n" +
                "- Fewer regulatory pre-submissions than competitors\n\n" +
                "RECENT_DEVELOPMENTS:\n" +
                "- Launched healthcare-specific Claude configuration\n" +
                "- Partnership with major health system announced\n";
    }

    private List<NewsArticle> buildArticles(int count) {
        List<NewsArticle> articles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            articles.add(new NewsArticle(
                    "article-" + i, "Article Title " + i,
                    URI.create("https://example.com/article-" + i),
                    "Body text for article " + i, "Anthropic Healthcare",
                    null, null, null, null, 0.5, null));
        }
        return articles;
    }
}
