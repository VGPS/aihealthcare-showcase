package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable configuration record holding the feature limits for a subscription tier.
 *
 * <p>Each tier (FREE, SUBSCRIBER) has a set of limits that control access to platform
 * features.  {@code archiveDays} determines how far back a subscriber can browse
 * articles (0 means unlimited).  {@code monthlyQueryLimit} caps the number of
 * AI-powered research queries per calendar month.
 *
 * <p>Instances are created from externalized configuration
 * ({@code aihealthcare.tiers.*} in {@code application.yml}) and injected into
 * {@link com.wgblackmon.aihealthcare.domain.service.TierGatingService}.
 *
 * @param archiveDays       Maximum article age in days visible to this tier; 0 = unlimited.
 * @param monthlyQueryLimit Maximum AI queries per month for this tier.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-07-20
 */
public record TierLimits(
        int archiveDays,
        int monthlyQueryLimit
) {
    /** Compact canonical constructor — validates non-negative values. */
    public TierLimits {
        if (archiveDays < 0) {
            throw new IllegalArgumentException("archiveDays must not be negative");
        }
        if (monthlyQueryLimit < 0) {
            throw new IllegalArgumentException("monthlyQueryLimit must not be negative");
        }
    }
}
