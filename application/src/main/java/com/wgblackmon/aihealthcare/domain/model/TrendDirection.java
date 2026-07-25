package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classification of a keyword's momentum across rolling time windows.
 *
 * <p>Used by the trend detection engine to label each tracked keyword
 * based on its frequency change between the recent window (30 days)
 * and the previous window (31–90 days).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-25
 */
public enum TrendDirection {

    /** Frequency increasing — momentum ratio > 1.5. */
    RISING,

    /** Frequency roughly constant — momentum ratio between 0.67 and 1.5. */
    STABLE,

    /** New entity or keyword — used by company profile service. */
    NEW
}
