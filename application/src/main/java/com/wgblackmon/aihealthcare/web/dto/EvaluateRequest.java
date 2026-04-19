package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Request DTO for evaluating a single prompt variant.
 *
 * @param variantId  the prompt variant to evaluate
 * @param articleIds IDs of articles to use as input
 * @param topic      the topic for summarization
 * @param tone       the newsletter tone (PROFESSIONAL, ACCESSIBLE, TECHNICAL)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
public record EvaluateRequest(
        String variantId,
        List<String> articleIds,
        String topic,
        String tone
) {}
