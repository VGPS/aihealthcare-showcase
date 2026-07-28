package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Deserialization records for the Perplexity Sonar Deep Research async API.
 *
 * <p>Maps the JSON response from both the submit endpoint
 * ({@code POST /v1/async/sonar}) and the poll endpoint
 * ({@code GET /v1/async/sonar/{id}}). The same top-level shape is used
 * for both — the {@code status} field indicates whether the job is still
 * running or has completed.
 *
 * <p>Key fields:
 * <ul>
 *   <li>{@code id} — job ID for polling</li>
 *   <li>{@code status} — {@code "pending"}, {@code "in_progress"},
 *       {@code "completed"}, or {@code "failed"}</li>
 *   <li>{@code choices[0].message.content} — the deep research report
 *       (may contain a leading {@code <think>...</think>} block)</li>
 *   <li>{@code usage} — token counts and per-category costs</li>
 *   <li>{@code search_results} — cited URLs with titles and snippets</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-28
 * @updated 2026-07-28
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeepResearchResponse(
        String id,
        String model,
        String status,
        List<Choice> choices,
        Usage usage,
        @JsonProperty("search_results") List<SearchResult> searchResults
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            int index,
            @JsonProperty("finish_reason") String finishReason,
            Message message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String role,
            String content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens,
            @JsonProperty("citation_tokens") int citationTokens,
            @JsonProperty("num_search_queries") int numSearchQueries,
            UsageCost cost
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsageCost(
            @JsonProperty("input_tokens_cost") double inputTokensCost,
            @JsonProperty("output_tokens_cost") double outputTokensCost,
            @JsonProperty("citation_tokens_cost") double citationTokensCost,
            @JsonProperty("reasoning_tokens_cost") double reasoningTokensCost,
            @JsonProperty("search_queries_cost") double searchQueriesCost
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResult(
            String title,
            String url,
            String snippet
    ) {}

    /**
     * Returns {@code true} if the job has reached a terminal state
     * ({@code "completed"} or {@code "failed"}).
     */
    public boolean isTerminal() {
        return "completed".equals(status) || "failed".equals(status);
    }

    /**
     * Returns {@code true} if the job completed successfully.
     */
    public boolean isCompleted() {
        return "completed".equals(status);
    }

    /**
     * Extracts the content from the first choice, or {@code null} if absent.
     */
    public String extractContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Message msg = choices.get(0).message();
        return msg != null ? msg.content() : null;
    }
}
