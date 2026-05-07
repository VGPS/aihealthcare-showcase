package com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Deserialization record for the Perplexity Sonar API chat-completions response.
 *
 * <p>Maps the JSON fields returned by
 * {@code POST https://api.perplexity.ai/chat/completions} into typed Java records.
 * Unknown fields are silently ignored via {@code @JsonIgnoreProperties(ignoreUnknown = true)}
 * so that Perplexity API additions do not break deserialization.
 *
 * <p>The {@code citations} list is the primary output used by
 * {@link PerplexityHarvester}: each entry is a URL to a source that Perplexity's
 * Sonar model cited when generating its response.  The inline citation markers
 * ({@code [1]}, {@code [2]}, etc.) in {@code choices[0].message.content} align
 * positionally with this list.
 *
 * <p>Example abbreviated response:
 * <pre>
 * {
 *   "id": "abc123",
 *   "choices": [{"message": {"role": "assistant", "content": "AI in healthcare [1]..."}}],
 *   "citations": ["https://pubmed.ncbi.nlm.nih.gov/..."]
 * }
 * </pre>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PerplexityApiResponse(
        @JsonProperty("id")        String id,
        @JsonProperty("choices")   List<PerplexityChoice> choices,
        @JsonProperty("citations") List<String> citations
) {

    /**
     * Represents a single completion choice returned by the Sonar model.
     *
     * @author  Bill Blackmon
     * @version 1.0
     * @since   2026-05-06
     * @updated 2026-05-06
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerplexityChoice(
            @JsonProperty("message") PerplexityMessage message
    ) {}

    /**
     * The assistant message content within a completion choice.
     *
     * @author  Bill Blackmon
     * @version 1.0
     * @since   2026-05-06
     * @updated 2026-05-06
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerplexityMessage(
            @JsonProperty("role")    String role,
            @JsonProperty("content") String content
    ) {}
}
