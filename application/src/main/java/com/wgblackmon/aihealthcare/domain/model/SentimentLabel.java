package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classification of news sentiment towards a company or topic.
 *
 * <p>Used by the sentiment analysis engine to label article-level
 * and aggregate company-level sentiment signals.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public enum SentimentLabel {

    /** Favourable coverage: funding, partnership, FDA clearance, growth. */
    POSITIVE,

    /** Unfavourable coverage: layoffs, recalls, lawsuits, clinical failures. */
    NEGATIVE,

    /** Coverage with both positive and negative signals. */
    MIXED,

    /** Factual reporting without clear positive or negative bias. */
    NEUTRAL
}
