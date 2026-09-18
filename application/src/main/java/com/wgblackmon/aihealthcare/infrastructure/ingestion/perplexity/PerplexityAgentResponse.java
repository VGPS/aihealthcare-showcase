package com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Deserialization record for the Perplexity Agent API response
 * ({@code POST https://api.perplexity.ai/v1/agent}).
 *
 * <p>Replaces the Sonar chat-completions response shape. The Agent API returns
 * a polymorphic {@code output[]} array containing typed items ({@code message},
 * {@code search_results}, etc.) instead of the old {@code choices[]} +
 * top-level {@code citations[]} structure.
 *
 * <p>Helper methods centralize text extraction ({@link #extractText()}) and
 * citation URL extraction ({@link #extractCitationUrls()}) so adapters do not
 * duplicate the output-walk logic.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-18
 * @updated 2026-09-18
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PerplexityAgentResponse(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("model") String model,
        @JsonProperty("output") List<OutputItem> output,
        @JsonProperty("usage") Usage usage,
        @JsonProperty("error") AgentError error,
        @JsonProperty("incomplete_details") IncompleteDetails incompleteDetails
) {

    /**
     * Extracts concatenated text from all {@code message} output items.
     *
     * @return the full response text, or {@code null} if no text output items exist
     */
    public String extractText() {
        if (output == null) return null;
        StringBuilder sb = new StringBuilder();
        for (OutputItem item : output) {
            if ("message".equals(item.type()) && item.content() != null) {
                for (ContentPart part : item.content()) {
                    if ("output_text".equals(part.type()) && part.text() != null) {
                        sb.append(part.text());
                    }
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Extracts citation URLs from the {@code search_results} output item.
     * Replaces the old top-level {@code citations[]} array from the Sonar API.
     *
     * @return list of cited URLs, never {@code null}
     */
    public List<String> extractCitationUrls() {
        if (output == null) return List.of();
        for (OutputItem item : output) {
            if ("search_results".equals(item.type()) && item.results() != null) {
                return item.results().stream()
                        .map(SearchResult::url)
                        .filter(url -> url != null && !url.isBlank())
                        .toList();
            }
        }
        return List.of();
    }

    /**
     * Extracts the full {@link SearchResult} records from the {@code search_results}
     * output item, for callers that need title/snippet in addition to URL.
     *
     * @return list of search results, never {@code null}
     */
    public List<SearchResult> extractSearchResults() {
        if (output == null) return List.of();
        for (OutputItem item : output) {
            if ("search_results".equals(item.type()) && item.results() != null) {
                return item.results();
            }
        }
        return List.of();
    }

    public boolean isCompleted() {
        return "completed".equals(status);
    }

    public boolean isFailed() {
        return "failed".equals(status) || "cancelled".equals(status);
    }

    /**
     * A single item in the Agent API {@code output[]} array.
     * Type-polymorphic: {@code content} is populated for {@code type="message"},
     * {@code results} for {@code type="search_results"}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputItem(
            @JsonProperty("type") String type,
            @JsonProperty("content") List<ContentPart> content,
            @JsonProperty("results") List<SearchResult> results
    ) {}

    /**
     * A content part within a {@code message} output item.
     * For text responses, {@code type="output_text"} and {@code text} holds the value.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentPart(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text
    ) {}

    /**
     * A search result entry within a {@code search_results} output item.
     * Replaces the old top-level {@code citations[]} URL strings.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResult(
            @JsonProperty("id") String id,
            @JsonProperty("url") String url,
            @JsonProperty("title") String title,
            @JsonProperty("snippet") String snippet,
            @JsonProperty("date") String date,
            @JsonProperty("last_updated") String lastUpdated,
            @JsonProperty("source") String source
    ) {}

    /**
     * Token usage and cost information.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens,
            @JsonProperty("total_tokens") int totalTokens,
            @JsonProperty("cost") UsageCost cost
    ) {}

    /**
     * Billing cost breakdown.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsageCost(
            @JsonProperty("input_cost") double inputCost,
            @JsonProperty("output_cost") double outputCost,
            @JsonProperty("tool_calls_cost") double toolCallsCost,
            @JsonProperty("total_cost") double totalCost,
            @JsonProperty("currency") String currency
    ) {}

    /**
     * Error details for failed or cancelled runs (HTTP 200 with non-completed status).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentError(
            @JsonProperty("message") String message,
            @JsonProperty("type") String type,
            @JsonProperty("code") int code
    ) {}

    /**
     * Details when status is {@code "incomplete"} — e.g. max_output_tokens hit.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncompleteDetails(
            @JsonProperty("reason") String reason
    ) {}
}
