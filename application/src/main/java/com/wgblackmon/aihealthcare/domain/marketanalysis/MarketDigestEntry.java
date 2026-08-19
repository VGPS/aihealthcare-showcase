package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.util.List;

/**
 * Aggregate root for a single market news item after LLM classification and enrichment.
 *
 * <p>Wraps a raw {@link MarketNewsItem} with the classifier's outputs: five
 * {@link ImpactAssessment} records (one per {@link ImpactDimension}), a
 * {@link FactClassification}, a {@link MarketImpactRank}, and the list of
 * {@link AffectedCompany} instances resolved from the news text.
 *
 * <p>Two convenience accessor methods delegate to the underlying {@code newsItem}
 * so the qualifying-bar filter in {@code MarketDigestService} can call
 * {@code entry.category()} and {@code entry.dealSizeUsd()} directly:
 *
 * <ul>
 *   <li>{@link #category()} — delegates to {@code newsItem.category()}
 *   <li>{@link #dealSizeUsd()} — delegates to {@code newsItem.dealSizeUsd()}, returning 0
 *       when the raw item has no disclosed deal amount
 * </ul>
 *
 * @param newsItem            the raw research item this entry was built from (required)
 * @param impactAssessments   five dimension assessments from the LLM classifier (required;
 *                            null yields empty list)
 * @param factClassification  CONFIRMED or SPECULATIVE as determined by the classifier (required)
 * @param rank                market impact rank 1–5 (required)
 * @param affectedCompanies   companies mentioned in the news item (required; null yields empty list)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record MarketDigestEntry(
        MarketNewsItem newsItem,
        List<ImpactAssessment> impactAssessments,
        FactClassification factClassification,
        MarketImpactRank rank,
        List<AffectedCompany> affectedCompanies
) {

    public MarketDigestEntry {
        if (newsItem == null) {
            throw new IllegalArgumentException("newsItem must not be null");
        }
        if (factClassification == null) {
            throw new IllegalArgumentException("factClassification must not be null");
        }
        if (rank == null) {
            throw new IllegalArgumentException("rank must not be null");
        }
        impactAssessments = impactAssessments == null ? List.of() : List.copyOf(impactAssessments);
        affectedCompanies = affectedCompanies == null ? List.of() : List.copyOf(affectedCompanies);
    }

    /**
     * Returns the {@link NewsCategory} of the underlying news item.
     * Convenience accessor for the qualifying-bar filter.
     */
    public NewsCategory category() {
        return newsItem.category();
    }

    /**
     * Returns the deal size in USD from the underlying news item, or {@code 0L} when
     * the news item has no disclosed deal amount (i.e. {@code newsItem.dealSizeUsd()} is null).
     * Convenience accessor for the qualifying-bar filter.
     */
    public long dealSizeUsd() {
        return newsItem.dealSizeUsd() != null ? newsItem.dealSizeUsd() : 0L;
    }
}
