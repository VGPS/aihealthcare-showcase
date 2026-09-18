package com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PerplexityAgentResponse} record and its helper methods.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-18
 * @updated 2026-09-18
 */
class PerplexityAgentResponseTest {

    @Test
    void isCompleted_trueForCompleted() {
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "completed", "perplexity/sonar", List.of(), null, null, null);
        assertThat(response.isCompleted()).isTrue();
        assertThat(response.isFailed()).isFalse();
    }

    @Test
    void isFailed_trueForFailed() {
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "failed", "perplexity/sonar", List.of(), null, null, null);
        assertThat(response.isFailed()).isTrue();
        assertThat(response.isCompleted()).isFalse();
    }

    @Test
    void isFailed_trueForCancelled() {
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "cancelled", "perplexity/sonar", List.of(), null, null, null);
        assertThat(response.isFailed()).isTrue();
        assertThat(response.isCompleted()).isFalse();
    }

    @Test
    void isCompleted_falseForQueued() {
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "queued", "perplexity/sonar", List.of(), null, null, null);
        assertThat(response.isCompleted()).isFalse();
        assertThat(response.isFailed()).isFalse();
    }

    @Test
    void isCompleted_falseForInProgress() {
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "in_progress", "perplexity/sonar", List.of(), null, null, null);
        assertThat(response.isCompleted()).isFalse();
        assertThat(response.isFailed()).isFalse();
    }

    @Test
    void extractText_returnsContentFromMessageOutputItem() {
        var contentPart = new PerplexityAgentResponse.ContentPart("output_text", "Report text.");
        var messageItem = new PerplexityAgentResponse.OutputItem("message", List.of(contentPart), null);
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "completed", "perplexity/sonar", List.of(messageItem), null, null, null);

        assertThat(response.extractText()).isEqualTo("Report text.");
    }

    @Test
    void extractText_concatenatesMultipleContentParts() {
        var part1 = new PerplexityAgentResponse.ContentPart("output_text", "Part one. ");
        var part2 = new PerplexityAgentResponse.ContentPart("output_text", "Part two.");
        var messageItem = new PerplexityAgentResponse.OutputItem("message", List.of(part1, part2), null);
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "completed", "perplexity/sonar", List.of(messageItem), null, null, null);

        assertThat(response.extractText()).isEqualTo("Part one. Part two.");
    }

    @Test
    void extractText_returnsNull_whenNoOutput() {
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "completed", "perplexity/sonar", List.of(), null, null, null);
        assertThat(response.extractText()).isNull();
    }

    @Test
    void extractText_returnsNull_whenOutputNull() {
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "completed", "perplexity/sonar", null, null, null, null);
        assertThat(response.extractText()).isNull();
    }

    @Test
    void extractText_skipsNonTextContentParts() {
        var textPart = new PerplexityAgentResponse.ContentPart("output_text", "Visible.");
        var otherPart = new PerplexityAgentResponse.ContentPart("other_type", "Hidden.");
        var messageItem = new PerplexityAgentResponse.OutputItem("message", List.of(textPart, otherPart), null);
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "completed", "perplexity/sonar", List.of(messageItem), null, null, null);

        assertThat(response.extractText()).isEqualTo("Visible.");
    }

    @Test
    void extractCitationUrls_returnsUrlsFromSearchResults() {
        var result1 = new PerplexityAgentResponse.SearchResult("1", "https://example.com/a", "Title A", "Snippet A", null, null, null);
        var result2 = new PerplexityAgentResponse.SearchResult("2", "https://example.com/b", "Title B", "Snippet B", null, null, null);
        var searchItem = new PerplexityAgentResponse.OutputItem("search_results", null, List.of(result1, result2));
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "completed", "perplexity/sonar", List.of(searchItem), null, null, null);

        assertThat(response.extractCitationUrls()).containsExactly("https://example.com/a", "https://example.com/b");
    }

    @Test
    void extractCitationUrls_returnsEmpty_whenNoSearchResults() {
        var messageItem = new PerplexityAgentResponse.OutputItem("message", List.of(), null);
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "completed", "perplexity/sonar", List.of(messageItem), null, null, null);

        assertThat(response.extractCitationUrls()).isEmpty();
    }

    @Test
    void extractCitationUrls_filtersBlankUrls() {
        var result1 = new PerplexityAgentResponse.SearchResult("1", "https://example.com/a", "Title", "Snippet", null, null, null);
        var result2 = new PerplexityAgentResponse.SearchResult("2", "", "Empty", "Snippet", null, null, null);
        var result3 = new PerplexityAgentResponse.SearchResult("3", null, "Null", "Snippet", null, null, null);
        var searchItem = new PerplexityAgentResponse.OutputItem("search_results", null, List.of(result1, result2, result3));
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "completed", "perplexity/sonar", List.of(searchItem), null, null, null);

        assertThat(response.extractCitationUrls()).containsExactly("https://example.com/a");
    }

    @Test
    void extractSearchResults_returnsFullRecords() {
        var result = new PerplexityAgentResponse.SearchResult("1", "https://example.com", "Title", "Snippet", "2026-01-01", "2026-01-02", "web");
        var searchItem = new PerplexityAgentResponse.OutputItem("search_results", null, List.of(result));
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "completed", "perplexity/sonar", List.of(searchItem), null, null, null);

        List<PerplexityAgentResponse.SearchResult> results = response.extractSearchResults();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).url()).isEqualTo("https://example.com");
        assertThat(results.get(0).title()).isEqualTo("Title");
        assertThat(results.get(0).snippet()).isEqualTo("Snippet");
    }

    @Test
    void extractSearchResults_returnsEmpty_whenOutputNull() {
        PerplexityAgentResponse response = new PerplexityAgentResponse(
                "resp_1", "completed", "perplexity/sonar", null, null, null, null);
        assertThat(response.extractSearchResults()).isEmpty();
    }
}
