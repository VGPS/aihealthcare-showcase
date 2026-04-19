package com.wgblackmon.aihealthcare.web.dto;

/**
 * Response DTO for evaluation quality scores.
 *
 * @param relevance          how well the summary reflects source articles [0.0, 1.0]
 * @param conciseness        brevity without loss of information [0.0, 1.0]
 * @param attributionQuality correctness of source citations [0.0, 1.0]
 * @param toneMatch          adherence to requested tone [0.0, 1.0]
 * @param completeness       coverage of major points [0.0, 1.0]
 * @param overall            weighted average [0.0, 1.0]
 * @param scoringNotes       AI-generated reasoning
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
public record EvaluationScoreResponse(
        double relevance,
        double conciseness,
        double attributionQuality,
        double toneMatch,
        double completeness,
        double overall,
        String scoringNotes
) {}
