package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record capturing the token usage of a single LLM call.
 *
 * <p>Written once per completed call by the infrastructure-layer logging advisor
 * that wraps every Spring AI {@code ChatClient} invocation. {@code pipelineId}
 * identifies which scheduled pipeline made the call (e.g. {@code "legal-trends"},
 * {@code "deal-signals"}) so cost can be attributed and acted on — throttled,
 * downgraded to a cheaper model, or paused — rather than just observed in
 * aggregate. Calls made outside any tagged pipeline (e.g. interactive web
 * requests) are recorded with {@code pipelineId = "interactive"}.
 *
 * @param id             database-generated surrogate key (null before persistence)
 * @param pipelineId     canonical pipeline identifier, or {@code "interactive"} for untagged calls
 * @param model          the model ID that served the call (e.g. {@code "claude-sonnet-4-6"})
 * @param promptTokens   input token count for this call
 * @param outputTokens   output/completion token count for this call
 * @param totalTokens    prompt + output tokens for this call
 * @param occurredAt     when the call completed
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public record LlmCallUsage(
        Long id,
        String pipelineId,
        String model,
        int promptTokens,
        int outputTokens,
        int totalTokens,
        Instant occurredAt
) {

    /**
     * Compact constructor — validates required fields.
     */
    public LlmCallUsage {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId must not be null or blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be null or blank");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
    }
}
