package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classification of a keyword's momentum across rolling time windows.
 *
 * <p>Used by the trend detection engine to label each tracked keyword
 * based on its frequency change between the recent window (30 days)
 * and the baseline window (91–180 days).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public enum TrendDirection {

    /** Frequency increasing — momentum ratio > 1.5. */
    RISING,

    /** Frequency roughly constant — momentum ratio between 0.67 and 1.5. */
    STABLE,

    /** Frequency declining — momentum ratio < 0.67. */
    FADING,

    /** Keyword appeared in the 30-day window but has zero baseline (91–180 days). */
    NEW
}
