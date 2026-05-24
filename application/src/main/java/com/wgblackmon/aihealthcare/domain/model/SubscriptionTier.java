package com.wgblackmon.aihealthcare.domain.model;

/**
 * Subscription tier that determines what newsletter content a subscriber receives.
 *
 * <p>Three tiers are supported:
 * <ul>
 *   <li>{@code FREE} — receives the standard weekly newsletter (current behaviour).</li>
 *   <li>{@code PREMIUM} — receives the standard newsletter plus premium-only
 *       sections (deep-dive analysis, vendor comparisons, research summaries).</li>
 *   <li>{@code ENTERPRISE} — receives everything in PREMIUM plus enterprise
 *       features (market intelligence reports, custom topic alerts).</li>
 * </ul>
 *
 * <p>Billing and payment processing are handled externally by Stripe Billing.
 * This enum represents only the content-gating tier stored locally; it is
 * updated via Stripe webhook events when a subscriber upgrades, downgrades,
 * or cancels.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-23
 * @updated 2026-05-23
 */
public enum SubscriptionTier {

    /** Standard free newsletter — no payment required. */
    FREE,

    /** Paid tier — standard newsletter plus premium-only sections. */
    PREMIUM,

    /** Paid tier — all premium content plus enterprise features. */
    ENTERPRISE
}
