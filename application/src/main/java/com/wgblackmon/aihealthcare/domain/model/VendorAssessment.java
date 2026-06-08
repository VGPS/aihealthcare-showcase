package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record representing a structured competitive assessment for a
 * single vendor within a {@link ResearchAnswer}.
 *
 * <p>This record is intended for use in market-intelligence prompts where the synthesis
 * step is asked to produce a per-vendor evaluation (strengths, weaknesses, relevance
 * to a specific healthcare AI use case).  It is not currently populated by the default
 * synthesis path but serves as the domain type for future structured-output prompts.
 *
 * <p>{@code relevanceScore} is a grounded value in [0.0, 1.0] computed as
 * {@code mentionCount / totalSources}, representing the fraction of retrieved source
 * documents that explicitly mention this vendor.
 *
 * @param vendorName     Name of the vendor or product (e.g., "Anthropic Claude").
 * @param strengths      Non-null list of strength descriptors; may be empty.
 * @param weaknesses     Non-null list of weakness descriptors; may be empty.
 * @param relevanceScore Grounded relevance: mentionCount / totalSources; in [0.0, 1.0].
 * @param mentionCount   Number of source documents that explicitly mention this vendor.
 * @param totalSources   Total number of source documents evaluated.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-04
 * @updated 2026-06-08
 */
public record VendorAssessment(
        String vendorName,
        List<String> strengths,
        List<String> weaknesses,
        double relevanceScore,
        int mentionCount,
        int totalSources
) {}
