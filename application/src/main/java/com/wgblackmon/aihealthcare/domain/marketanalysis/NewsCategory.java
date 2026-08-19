package com.wgblackmon.aihealthcare.domain.marketanalysis;

/**
 * Category of a market-moving news event in the AI-healthcare sector.
 *
 * <p>Used by {@link MarketDigestEntry} and the qualifying-bar filter
 * ({@code MarketDigestService.isMarketMoving()}) to determine whether a news item
 * warrants inclusion in the daily digest and subscriber notification.
 *
 * <p>{@code FUNDING} items only qualify when the deal size exceeds $50M (strict inequality).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public enum NewsCategory {

    /** Earnings release, guidance update, or material financial disclosure. */
    EARNINGS,

    /** Regulatory ruling, agency action, or rulemaking event (FDA, EU AI Act, CMS, etc.). */
    REGULATORY,

    /** Private or public funding round. Qualifies only when deal size > $50,000,000. */
    FUNDING,

    /** Merger, acquisition, or divestiture. */
    M_AND_A,

    /** Major partnership, licensing deal, or large pharma AI collaboration. */
    MAJOR_PARTNERSHIP,

    /** News that does not fit any qualifying category. Never triggers a notification. */
    OTHER
}
