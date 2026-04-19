package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for a side-by-side comparison of prompt variants.
 *
 * @param comparisonId unique identifier for this comparison
 * @param articleIds   IDs of articles used as shared input
 * @param topic        topic used during summarization
 * @param tone         tone used during summarization
 * @param results      individual evaluation results for each variant
 * @param comparedAt   timestamp of the comparison
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
public record ComparisonResultResponse(
        String comparisonId,
        List<String> articleIds,
        String topic,
        String tone,
        List<EvaluationResultResponse> results,
        Instant comparedAt
) {}
