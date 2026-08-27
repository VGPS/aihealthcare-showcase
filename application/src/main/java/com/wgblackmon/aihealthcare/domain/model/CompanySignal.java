package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Computed signal data for an AI healthcare company, derived by cross-referencing
 * article frequency (90-day window), deal signals, and sentiment analysis.
 *
 * <p>Used by the company directory to rank, sort, and highlight companies based
 * on current market activity. All three signal dimensions are optional — the service
 * degrades gracefully when data is absent. A company with no articles, no deals,
 * and no sentiment run will have a relevance score of zero and all flags false.
 *
 * <p>Relevance score formula (0–50 typical range):
 * <ul>
 *   <li>Article velocity: {@code min(articleCount90d × 3, 30)}</li>
 *   <li>Funding bonus: +20 if a FUNDING deal signal exists</li>
 *   <li>Sentiment penalty: −15 if sentimentScore &lt; −0.2</li>
 * </ul>
 *
 * @param companyId        matches {@link HealthcareAiCompany#companyId()}
 * @param articleCount90d  number of articles with this company's name in the title, last 90 days
 * @param latestDealType   deal type (e.g. "FUNDING"), null if no deal signals exist
 * @param latestDealAmount deal amount string (e.g. "$200M", "undisclosed"), null if no deal
 * @param latestDealDate   detection timestamp of the most recent deal signal, null if no deal
 * @param sentimentScore   continuous −1.0 to +1.0 score; 0.0 when no sentiment data
 * @param sentimentLabel   "POSITIVE", "NEGATIVE", "MIXED", "NEUTRAL", or null
 * @param hasSentimentData true if a sentiment analysis snapshot exists for this company
 * @param relevanceScore   composite score used for default directory ranking (non-negative)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-27
 * @updated 2026-08-27
 */
public record CompanySignal(
        String companyId,
        int articleCount90d,
        String latestDealType,
        String latestDealAmount,
        Instant latestDealDate,
        double sentimentScore,
        String sentimentLabel,
        boolean hasSentimentData,
        int relevanceScore
) {

    /** True when this company has a detected FUNDING deal signal. */
    public boolean hasRecentFunding() {
        return "FUNDING".equals(latestDealType);
    }

    /** True when sentiment analysis indicates elevated risk (score below −0.2). */
    public boolean isWatchList() {
        return hasSentimentData && sentimentScore < -0.2;
    }

    /** True when the company appears in 3 or more article titles in the last 90 days. */
    public boolean isTrending() {
        return articleCount90d >= 3;
    }
}
