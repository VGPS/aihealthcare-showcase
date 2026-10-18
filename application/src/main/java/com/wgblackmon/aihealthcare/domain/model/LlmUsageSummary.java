package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record aggregating {@link LlmCallUsage} rows under a single
 * grouping key — a model ID, a pipeline ID, or a calendar day depending on which
 * grouping {@link com.wgblackmon.aihealthcare.domain.service.LlmUsageSummaryService}
 * method produced it.
 *
 * @param key              the grouping value (model ID, pipeline ID, or ISO date string)
 * @param callCount        number of LLM calls aggregated under this key
 * @param promptTokens     summed input tokens
 * @param outputTokens     summed output tokens
 * @param totalTokens      summed total tokens
 * @param estimatedCostUsd estimated USD cost; 0 if the model has no configured pricing
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public record LlmUsageSummary(
        String key,
        long callCount,
        long promptTokens,
        long outputTokens,
        long totalTokens,
        double estimatedCostUsd
) {
}
