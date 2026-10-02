package com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Deserialization record for the Perplexity Chat Completions API response
 * ({@code POST https://api.perplexity.ai/v1/chat/completions}).
 *
 * <p>Used by {@code PerplexityDeepResearchAdapter} for the {@code sonar-deep-research}
 * model, which targets the chat completions endpoint rather than the Agents API.
 * Deep Research responses can take 30–60 seconds and include reasoning tokens
 * (surfaced under {@code choices[0].message.content} after synthesis).
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} absorbs the
 * {@code reasoning}, {@code delta}, and {@code search_results} fields returned
 * by some model variants without requiring matching record components.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-02
 * @updated 2026-10-02
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PerplexityChatCompletionResponse(
        @JsonProperty("id")      String id,
        @JsonProperty("model")   String model,
        @JsonProperty("choices") List<Choice> choices,
        @JsonProperty("usage")   Usage usage,
        @JsonProperty("citations") List<String> citations
) {

    /**
     * Returns the assistant message content from the first choice, or
     * {@code null} if the response contains no choices or the message is empty.
     *
     * @return synthesized text, or {@code null}
     */
    public String extractText() {
        if (choices == null || choices.isEmpty()) return null;
        Choice first = choices.get(0);
        if (first.message() == null) return null;
        String content = first.message().content();
        return (content == null || content.isBlank()) ? null : content;
    }

    /** A single completion choice. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            @JsonProperty("index")         int index,
            @JsonProperty("finish_reason") String finishReason,
            @JsonProperty("message")       Message message
    ) {}

    /** The assistant message within a choice. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("role")    String role,
            @JsonProperty("content") String content
    ) {}

    /** Token usage summary. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens")     int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens")      int totalTokens
    ) {}
}
