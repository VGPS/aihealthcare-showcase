package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiSearchResponseParser}.
 *
 * <p>Verifies the shared response parsing logic (SUMMARY/KEY_FINDINGS
 * extraction, NO_MATCH handling, null/blank fallback, truncation) and
 * prompt building (article formatting, template placeholder replacement,
 * body text truncation).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-11
 * @updated 2026-09-11
 */
class AiSearchResponseParserTest {

    private AiSearchResponseParser parser;
    private PromptLoaderService promptLoaderService;

    private static final String PROMPT_TEMPLATE =
            "Search query: {query}\nRetrieved articles ({articleCount} total):\n{articles}\nSUMMARY and KEY_FINDINGS";

    @BeforeEach
    void setUp() {
        promptLoaderService = mock(PromptLoaderService.class);
        when(promptLoaderService.load("ai-search-synthesis.txt")).thenReturn(PROMPT_TEMPLATE);
        parser = new AiSearchResponseParser(promptLoaderService);
    }

    private NewsArticle sampleArticle(String id, String title, String body) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                body, "AI Healthcare", "Dr. Smith",
                1L, "PubMed", "ACADEMIC", 0.9, Instant.now());
    }

    @Test
    void parseResponse_validResponse_extractsSummaryAndFindings() {
        String response = "SUMMARY: AI diagnostics are transforming radiology workflows.\n"
                + "KEY_FINDINGS:\n"
                + "- Accuracy improved by 15%\n"
                + "- Adoption rate doubled in 2025\n"
                + "* Third finding via asterisk";

        AiSearchSynthesis result = parser.parseResponse(response, "TestModel");

        assertThat(result).isNotNull();
        assertThat(result.modelName()).isEqualTo("TestModel");
        assertThat(result.summary()).isEqualTo("AI diagnostics are transforming radiology workflows.");
        assertThat(result.keyFindings()).hasSize(3);
        assertThat(result.keyFindings().get(0)).isEqualTo("Accuracy improved by 15%");
        assertThat(result.keyFindings().get(1)).isEqualTo("Adoption rate doubled in 2025");
        assertThat(result.keyFindings().get(2)).isEqualTo("Third finding via asterisk");
        assertThat(result.generatedAt()).isNotNull();
    }

    @Test
    void parseResponse_nullResponse_returnsFallback() {
        AiSearchSynthesis result = parser.parseResponse(null, "Claude");

        assertThat(result).isNotNull();
        assertThat(result.modelName()).isEqualTo("Claude");
        assertThat(result.summary()).isEqualTo("No synthesis available.");
        assertThat(result.keyFindings()).isEmpty();
    }

    @Test
    void parseResponse_blankResponse_returnsFallback() {
        AiSearchSynthesis result = parser.parseResponse("   ", "GPT");

        assertThat(result).isNotNull();
        assertThat(result.modelName()).isEqualTo("GPT");
        assertThat(result.summary()).isEqualTo("No synthesis available.");
        assertThat(result.keyFindings()).isEmpty();
    }

    @Test
    void parseResponse_noMatchResponse_returnsNull() {
        AiSearchSynthesis result = parser.parseResponse("NO_MATCH: Articles are not relevant to the query.", "Perplexity");

        assertThat(result).isNull();
    }

    @Test
    void parseResponse_noSummaryLine_usesFullResponse() {
        String response = "This is a free-form response without the expected format.";

        AiSearchSynthesis result = parser.parseResponse(response, "Gemini");

        assertThat(result).isNotNull();
        assertThat(result.modelName()).isEqualTo("Gemini");
        assertThat(result.summary()).isEqualTo("This is a free-form response without the expected format.");
        assertThat(result.keyFindings()).isEmpty();
    }

    @Test
    void parseResponse_longResponse_truncatesAt4000() {
        String longResponse = "A".repeat(5000);

        AiSearchSynthesis result = parser.parseResponse(longResponse, "Claude");

        assertThat(result).isNotNull();
        assertThat(result.summary()).hasSize(4003); // 4000 chars + "..."
        assertThat(result.summary()).endsWith("...");
    }

    @Test
    void buildPrompt_formatsArticlesCorrectly() {
        NewsArticle article = sampleArticle("a1", "AI in Radiology", "Body text for AI in Radiology");

        String prompt = parser.buildPrompt("AI diagnostics", List.of(article));

        assertThat(prompt).contains("Search query: AI diagnostics");
        assertThat(prompt).contains("Retrieved articles (1 total):");
        assertThat(prompt).contains("[1] Title: AI in Radiology");
        assertThat(prompt).contains("Author: Dr. Smith");
        assertThat(prompt).contains("Source: PubMed");
        assertThat(prompt).contains("Body:   Body text for AI in Radiology");
    }

    @Test
    void buildPrompt_truncatesLongBody() {
        String longBody = "X".repeat(600);
        NewsArticle article = sampleArticle("a1", "Test Article", longBody);

        String prompt = parser.buildPrompt("test query", List.of(article));

        assertThat(prompt).contains("X".repeat(500) + "...");
        assertThat(prompt).doesNotContain("X".repeat(501));
    }
}
