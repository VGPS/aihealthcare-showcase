package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.EvaluationScore;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiEvaluationAdapter}.
 *
 * <p>Uses a mocked {@link ChatClient} to verify prompt construction and response
 * parsing without making real LLM calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiEvaluationAdapterTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private PromptLoaderService promptLoaderService;

    private AiEvaluationAdapter adapter;

    private static final NewsletterSection SECTION = new NewsletterSection(
            "sec-001", SectionType.WHAT_SHIPPED, "AI Healthcare",
            "AI Advances in Diagnostics", "Summary of advances in AI diagnostics.",
            List.of("art-001"));

    private static final NewsArticle ARTICLE = new NewsArticle(
            "art-001", "AI Diagnostics Breakthrough",
            URI.create("https://example.com/article1"),
            "Full body text of the article about AI diagnostics.",
            "AI Healthcare", "Dr. Smith", null, "PubMed", "ACADEMIC", 0.9,
            Instant.parse("2026-04-17T00:00:00Z"));

    private static final String WELL_FORMED_RESPONSE =
            "RELEVANCE: 0.85\n" +
            "CONCISENESS: 0.90\n" +
            "ATTRIBUTION_QUALITY: 0.75\n" +
            "TONE_MATCH: 0.80\n" +
            "COMPLETENESS: 0.70\n" +
            "OVERALL: 0.80\n" +
            "NOTES: Solid output with good relevance and conciseness.";

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(promptLoaderService.load("evaluate-section.txt"))
                .thenReturn("Evaluate {topic} {tone} {headline} {summary} {sectionType} {articles}");
        adapter = new AiEvaluationAdapter(chatClientBuilder, promptLoaderService);
    }

    @Test
    void evaluate_wellFormedResponse_parsesAllScores() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(WELL_FORMED_RESPONSE);

        EvaluationScore result = adapter.evaluate(SECTION, List.of(ARTICLE), "AI Healthcare", NewsletterTone.PROFESSIONAL);

        assertThat(result.relevance()).isEqualTo(0.85);
        assertThat(result.conciseness()).isEqualTo(0.90);
        assertThat(result.attributionQuality()).isEqualTo(0.75);
        assertThat(result.toneMatch()).isEqualTo(0.80);
        assertThat(result.completeness()).isEqualTo(0.70);
        assertThat(result.overall()).isEqualTo(0.80);
        assertThat(result.scoringNotes()).isEqualTo("Solid output with good relevance and conciseness.");
    }

    @Test
    void evaluate_callsChatClientWithPrompt() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(WELL_FORMED_RESPONSE);

        adapter.evaluate(SECTION, List.of(ARTICLE), "AI Healthcare", NewsletterTone.PROFESSIONAL);

        verify(chatClient).prompt(anyString());
    }

    @Test
    void parseScore_missingFields_defaultsToHalf() {
        String partialResponse = "RELEVANCE: 0.85\nNOTES: Only relevance provided.";

        EvaluationScore result = adapter.parseScore(partialResponse);

        assertThat(result.relevance()).isEqualTo(0.85);
        assertThat(result.conciseness()).isEqualTo(0.5);
        assertThat(result.attributionQuality()).isEqualTo(0.5);
        assertThat(result.toneMatch()).isEqualTo(0.5);
        assertThat(result.completeness()).isEqualTo(0.5);
        assertThat(result.overall()).isEqualTo(0.5);
        assertThat(result.scoringNotes()).isEqualTo("Only relevance provided.");
    }

    @Test
    void parseScore_unparseable_defaultsToHalf() {
        String badResponse = "RELEVANCE: abc\nCONCISENESS: xyz\nOVERALL: 0.80\nNOTES: Bad input.";

        EvaluationScore result = adapter.parseScore(badResponse);

        assertThat(result.relevance()).isEqualTo(0.5);
        assertThat(result.conciseness()).isEqualTo(0.5);
        assertThat(result.overall()).isEqualTo(0.80);
    }

    @Test
    void parseScore_scoresAboveOne_clampedToOne() {
        String overResponse = "RELEVANCE: 1.5\nCONCISENESS: 2.0\nOVERALL: 0.80\nNOTES: Clamped.";

        EvaluationScore result = adapter.parseScore(overResponse);

        assertThat(result.relevance()).isEqualTo(1.0);
        assertThat(result.conciseness()).isEqualTo(1.0);
    }

    @Test
    void parseScore_scoresBelowZero_clampedToZero() {
        String underResponse = "RELEVANCE: -0.5\nCONCISENESS: -1.0\nOVERALL: 0.50\nNOTES: Clamped.";

        EvaluationScore result = adapter.parseScore(underResponse);

        assertThat(result.relevance()).isEqualTo(0.0);
        assertThat(result.conciseness()).isEqualTo(0.0);
    }

    @Test
    void parseScore_emptyResponse_allDefaults() {
        EvaluationScore result = adapter.parseScore("");

        assertThat(result.relevance()).isEqualTo(0.5);
        assertThat(result.conciseness()).isEqualTo(0.5);
        assertThat(result.attributionQuality()).isEqualTo(0.5);
        assertThat(result.toneMatch()).isEqualTo(0.5);
        assertThat(result.completeness()).isEqualTo(0.5);
        assertThat(result.overall()).isEqualTo(0.5);
        assertThat(result.scoringNotes()).isEmpty();
    }

    @Test
    void parseScore_perfectScores_allOnes() {
        String perfectResponse =
                "RELEVANCE: 1.00\n" +
                "CONCISENESS: 1.00\n" +
                "ATTRIBUTION_QUALITY: 1.00\n" +
                "TONE_MATCH: 1.00\n" +
                "COMPLETENESS: 1.00\n" +
                "OVERALL: 1.00\n" +
                "NOTES: Perfect output.";

        EvaluationScore result = adapter.parseScore(perfectResponse);

        assertThat(result.relevance()).isEqualTo(1.0);
        assertThat(result.conciseness()).isEqualTo(1.0);
        assertThat(result.attributionQuality()).isEqualTo(1.0);
        assertThat(result.toneMatch()).isEqualTo(1.0);
        assertThat(result.completeness()).isEqualTo(1.0);
        assertThat(result.overall()).isEqualTo(1.0);
        assertThat(result.scoringNotes()).isEqualTo("Perfect output.");
    }

    @Test
    void parseScore_missingNotes_emptyString() {
        String noNotes = "RELEVANCE: 0.80\nOVERALL: 0.80";

        EvaluationScore result = adapter.parseScore(noNotes);

        assertThat(result.scoringNotes()).isEmpty();
    }
}
