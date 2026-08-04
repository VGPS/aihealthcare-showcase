package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.ArticleSentiment;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SentimentAnalysisAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
class SentimentAnalysisAdapterTest {

    private SentimentAnalysisAdapter adapter;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);
        PromptLoaderService promptLoader = mock(PromptLoaderService.class);
        when(promptLoader.load("sentiment-analysis.txt")).thenReturn(
                "Company: {companyName}\nARTICLES:\n{numberedArticleList}");
        adapter = new SentimentAnalysisAdapter(builder, promptLoader);
    }

    @Test
    void parseResponse_parsesValidOutput() {
        String response = "SENTIMENT_RESULTS:\n" +
                "[1] POSITIVE | 0.92 | Company announced new AI tool for healthcare\n" +
                "[2] NEGATIVE | 0.85 | FDA issued warning letter about device\n" +
                "[3] NEUTRAL | 0.70 | Article mentions company in passing\n";

        List<NewsArticle> articles = List.of(
                buildArticle("a1", "AI Tool Launch"),
                buildArticle("a2", "FDA Warning"),
                buildArticle("a3", "Industry Roundup")
        );

        List<ArticleSentiment> result = adapter.parseResponse(response, articles);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).sentiment()).isEqualTo(SentimentLabel.POSITIVE);
        assertThat(result.get(0).confidence()).isEqualTo(0.92);
        assertThat(result.get(1).sentiment()).isEqualTo(SentimentLabel.NEGATIVE);
        assertThat(result.get(2).sentiment()).isEqualTo(SentimentLabel.NEUTRAL);
    }

    @Test
    void parseResponse_handlesMixedLabel() {
        String response = "SENTIMENT_RESULTS:\n" +
                "[1] MIXED | 0.75 | Both positive and negative signals present\n";

        List<NewsArticle> articles = List.of(buildArticle("a1", "Complex News"));

        List<ArticleSentiment> result = adapter.parseResponse(response, articles);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sentiment()).isEqualTo(SentimentLabel.MIXED);
    }

    @Test
    void parseResponse_skipsInvalidLines() {
        String response = "SENTIMENT_RESULTS:\n" +
                "[1] POSITIVE | 0.90 | Good news\n" +
                "This is not a valid line\n" +
                "[99] POSITIVE | 0.80 | Out of range\n" +
                "[2] UNKNOWN_LABEL | 0.80 | Bad label\n";

        List<NewsArticle> articles = List.of(
                buildArticle("a1", "Good News"),
                buildArticle("a2", "More News")
        );

        List<ArticleSentiment> result = adapter.parseResponse(response, articles);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).articleId()).isEqualTo("a1");
    }

    @Test
    void parseResponse_emptyResponse_returnsEmptyList() {
        List<ArticleSentiment> result = adapter.parseResponse(
                "", List.of(buildArticle("a1", "Title")));
        assertThat(result).isEmpty();
    }

    @Test
    void parseResponse_clampsConfidence() {
        String response = "SENTIMENT_RESULTS:\n" +
                "[1] POSITIVE | 1.5 | Over confidence\n";

        List<NewsArticle> articles = List.of(buildArticle("a1", "Title"));

        List<ArticleSentiment> result = adapter.parseResponse(response, articles);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).confidence()).isEqualTo(1.0);
    }

    @Test
    void buildPrompt_includesCompanyAndArticles() {
        List<NewsArticle> articles = List.of(
                buildArticle("a1", "Tempus raises $100M"),
                buildArticle("a2", "Tempus launches platform")
        );

        String prompt = adapter.buildPrompt(articles, "Tempus AI");

        assertThat(prompt).contains("Tempus AI");
        assertThat(prompt).contains("[1] Tempus raises $100M");
        assertThat(prompt).contains("[2] Tempus launches platform");
    }

    @Test
    void analyzeSentiment_nullInput_returnsEmptyList() {
        List<ArticleSentiment> result = adapter.analyzeSentiment(null, "Company");
        assertThat(result).isEmpty();
    }

    @Test
    void analyzeSentiment_emptyInput_returnsEmptyList() {
        List<ArticleSentiment> result = adapter.analyzeSentiment(List.of(), "Company");
        assertThat(result).isEmpty();
    }

    private NewsArticle buildArticle(String id, String title) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                "body", "topic", null, null, null, null, 0.5, null);
    }
}
