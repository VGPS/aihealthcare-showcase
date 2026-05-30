package com.wgblackmon.aihealthcare.domain.model;

/**
 * Subscription tier that determines what newsletter content a subscriber receives
 * and what platform features are available.
 *
 * <p>Two tiers are supported:
 * <ul>
 *   <li>{@code FREE} — receives a teaser newsletter, limited archive (7 days),
 *       basic search, and a capped number of AI queries per month.</li>
 *   <li>{@code MEMBER} — receives the full newsletter plus premium features:
 *       full archive, semantic search, higher AI query allowance, and model choice.</li>
 * </ul>
 *
 * <p>Billing and payment processing are handled externally by Stripe Billing.
 * This enum represents only the content-gating tier stored locally; it is
 * updated via Stripe webhook events when a subscriber upgrades or cancels.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-23
 * @updated 2026-05-26
 */
public enum SubscriptionTier {

    /** Standard free tier — no payment required, limited features. */
    FREE,

    /** Paid tier — full newsletter, full archive, semantic search, higher query limits. */
    MEMBER
}
