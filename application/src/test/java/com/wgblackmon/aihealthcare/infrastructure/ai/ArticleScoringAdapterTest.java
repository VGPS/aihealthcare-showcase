package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ArticleScoringAdapter}.
 *
 * <p>Tests prompt building and response parsing without making real LLM calls.
 * The adapter's internal methods are package-private to allow direct testing.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-24
 * @updated 2026-07-24
 */
class ArticleScoringAdapterTest {

    private ArticleScoringAdapter adapter;
    private PromptLoaderService promptLoaderService;

    private static final String PROMPT_TEMPLATE =
            "You are scoring healthcare AI articles.\n" +
            "Theme: {themeName} — {themeDescription}\n" +
            "Score threshold: {scoreThreshold}\n" +
            "Articles:\n{numberedArticleList}\n" +
            "SCORED_ARTICLES:";

    @BeforeEach
    void setUp() {
        promptLoaderService = mock(PromptLoaderService.class);
        when(promptLoaderService.load("tech-trend-score.txt")).thenReturn(PROMPT_TEMPLATE);

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        adapter = new ArticleScoringAdapter(builder, promptLoaderService);
    }

    @Test
    void buildPrompt_includesThemeAndArticles() {
        List<NewsArticle> articles = List.of(
                makeArticle("art-1", "FDA clears AI diagnostic tool"),
                makeArticle("art-2", "Market report on telehealth growth")
        );

        String prompt = adapter.buildPrompt(articles, "radiology ai",
                "AI applications in radiology imaging", 7);

        assertThat(prompt).contains("radiology ai");
        assertThat(prompt).contains("AI applications in radiology imaging");
        assertThat(prompt).contains("[1] FDA clears AI diagnostic tool");
        assertThat(prompt).contains("[2] Market report on telehealth growth");
        assertThat(prompt).contains("7");
    }

    @Test
    void buildPrompt_truncatesLongBodyText() {
        String longBody = "A".repeat(500);
        NewsArticle article = new NewsArticle("art-1", "Title", URI.create("https://example.com"),
                longBody, "topic", null, null, "Source", "INDUSTRY", 0.5, Instant.now());

        String prompt = adapter.buildPrompt(List.of(article), "theme", "desc", 7);

        // Body should be truncated to 300 chars + "..."
        assertThat(prompt).contains("...");
        assertThat(prompt).doesNotContain("A".repeat(400));
    }

    @Test
    void parseResponse_extractsScoredArticles() {
        List<NewsArticle> articles = List.of(
                makeArticle("art-1", "FDA clears AI diagnostic"),
                makeArticle("art-2", "Market overview report"),
                makeArticle("art-3", "Breakthrough clinical trial result")
        );

        String response = "Some preamble text\n" +
                "SCORED_ARTICLES:\n" +
                "[1] SCORE: 8 | First autonomous AI diagnostic cleared by FDA\n" +
                "[3] SCORE: 9 | Phase 3 trial shows 40% improvement in detection accuracy\n";

        List<ScoredArticle> result = adapter.parseResponse(response, articles, "radiology ai", 0);

        assertThat(result).hasSize(2);

        assertThat(result.get(0).articleId()).isEqualTo("art-1");
        assertThat(result.get(0).score()).isEqualTo(8);
        assertThat(result.get(0).rationale()).isEqualTo("First autonomous AI diagnostic cleared by FDA");
        assertThat(result.get(0).keyword()).isEqualTo("radiology ai");

        assertThat(result.get(1).articleId()).isEqualTo("art-3");
        assertThat(result.get(1).score()).isEqualTo(9);
    }

    @Test
    void parseResponse_skipsInvalidLines() {
        List<NewsArticle> articles = List.of(
                makeArticle("art-1", "Valid article")
        );

        String response = "SCORED_ARTICLES:\n" +
                "[1] SCORE: 8 | Valid rationale\n" +
                "Some garbage line\n" +
                "[99] SCORE: 7 | Out of range index\n" +
                "[1] SCORE: 15 | Invalid score\n";

        List<ScoredArticle> result = adapter.parseResponse(response, articles, "theme", 0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(8);
    }

    @Test
    void parseResponse_emptyResponse_returnsEmptyList() {
        List<NewsArticle> articles = List.of(makeArticle("art-1", "Title"));

        List<ScoredArticle> result = adapter.parseResponse("", articles, "theme", 0);

        assertThat(result).isEmpty();
    }

    @Test
    void parseResponse_noScoredSection_returnsEmptyList() {
        List<NewsArticle> articles = List.of(makeArticle("art-1", "Title"));

        String response = "I found no articles meeting the threshold.\n" +
                "All articles were routine market updates.";

        List<ScoredArticle> result = adapter.parseResponse(response, articles, "theme", 0);

        assertThat(result).isEmpty();
    }

    @Test
    void parseResponse_skipsDuplicateRationale() {
        List<NewsArticle> articles = List.of(
                makeArticle("art-1", "FDA clears AI diagnostic"),
                makeArticle("art-2", "FDA clears AI diagnostic (syndicated)"),
                makeArticle("art-3", "Breakthrough clinical trial result")
        );

        String response = "SCORED_ARTICLES:\n" +
                "[1] SCORE: 8 | First autonomous AI diagnostic cleared by FDA\n" +
                "[2] SCORE: 8 | Duplicate of [1] — same article republished on another site\n" +
                "[3] SCORE: 9 | Phase 3 trial shows 40% improvement in detection accuracy\n";

        List<ScoredArticle> result = adapter.parseResponse(response, articles, "radiology ai", 0);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).articleId()).isEqualTo("art-1");
        assertThat(result.get(1).articleId()).isEqualTo("art-3");
    }

    @Test
    void scoreArticles_emptyInput_returnsEmptyList() {
        List<ScoredArticle> result = adapter.scoreArticles(List.of(), "theme", "desc", 7);

        assertThat(result).isEmpty();
    }

    @Test
    void scoreArticles_nullInput_returnsEmptyList() {
        List<ScoredArticle> result = adapter.scoreArticles(null, "theme", "desc", 7);

        assertThat(result).isEmpty();
    }

    private NewsArticle makeArticle(String id, String title) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                null, "topic", null, null, "Source", "INDUSTRY", 0.5, Instant.now());
    }
}
