package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for a full evaluation result.
 *
 * @param evaluationId unique identifier for this evaluation
 * @param variantId    the variant that was evaluated
 * @param variantName  human-readable variant label
 * @param articleIds   IDs of articles used as input
 * @param topic        topic used during summarization
 * @param tone         tone used during summarization
 * @param section      the AI-generated section output (with article IDs for attribution)
 * @param score        quality scores from the AI evaluator
 * @param evaluatedAt  timestamp of the evaluation
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
public record EvaluationResultResponse(
        String evaluationId,
        String variantId,
        String variantName,
        List<String> articleIds,
        String topic,
        String tone,
        EvalSectionResponse section,
        EvaluationScoreResponse score,
        Instant evaluatedAt
) {}
