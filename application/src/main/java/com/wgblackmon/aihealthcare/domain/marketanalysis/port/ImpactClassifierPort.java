package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;

import java.util.List;

/**
 * Outbound port for LLM-powered classification and impact scoring of market news entries.
 *
 * <p>The Claude adapter ({@code ClaudeImpactClassifierAdapter}) implements this port using
 * the existing Spring AI {@code ChatClient} wired to the direct Anthropic API. For each
 * entry, the classifier produces a {@link com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification},
 * five {@link com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactAssessment} records
 * (one per {@link com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDimension}),
 * and a {@link com.wgblackmon.aihealthcare.domain.marketanalysis.MarketImpactRank} —
 * all in a single structured LLM call.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface ImpactClassifierPort {

    /**
     * Classifies each entry, returning a new list of entries with
     * {@code factClassification}, {@code impactAssessments}, and {@code rank} populated.
     * The input entries need not have these fields set — the classifier fills them in.
     *
     * @param entries entries to classify (non-null; may be empty)
     * @return classified entries in the same order as the input
     */
    List<MarketDigestEntry> classify(List<MarketDigestEntry> entries);
}
