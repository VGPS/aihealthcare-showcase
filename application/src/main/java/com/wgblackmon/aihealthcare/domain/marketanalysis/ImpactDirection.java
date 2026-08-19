package com.wgblackmon.aihealthcare.domain.marketanalysis;

/**
 * Direction of impact along a single {@link ImpactDimension} for a market news item.
 *
 * <p>Used within {@link ImpactAssessment} to characterize whether the event is
 * favorable, unfavorable, or neutral for the assessed dimension.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public enum ImpactDirection {

    /** Event is expected to benefit this dimension. */
    POSITIVE,

    /** Event is expected to harm this dimension. */
    NEGATIVE,

    /** Event has no material expected effect on this dimension. */
    NEUTRAL
}
