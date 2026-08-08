package com.wgblackmon.aihealthcare.domain.model;

/**
 * Subscription tier that determines what newsletter content a subscriber receives
 * and what platform features are available.
 *
 * <p>Five tiers are supported:
 * <ul>
 *   <li>{@code DEMO} — full site access for 7 days; same feature limits as
 *       SUBSCRIBER.  Expires at the timestamp in {@code AppUser.demoExpiresAt}.</li>
 *   <li>{@code FREE_PENDING} — short-lived post-demo-expiration state; the user
 *       must choose FREE (email-only) or SUBSCRIBER (paid).  No app access.</li>
 *   <li>{@code FREE} — email-only daily newsletter; no app login
 *       ({@code app_users.enabled = false}).</li>
 *   <li>{@code SUBSCRIBER} — $19/month paid tier; full newsletter, full archive,
 *       semantic search, higher AI query allowance, and model choice.</li>
 *   <li>{@code ENTERPRISE} — enterprise API tier; highest query limits (2000/month),
 *       10 API keys, premium API access for integration into customer systems.</li>
 * </ul>
 *
 * <p>Billing and payment processing are handled externally by Stripe Billing.
 * This enum represents only the content-gating tier stored locally; it is
 * updated via Stripe webhook events when a subscriber upgrades or cancels.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-05-23
 * @updated 2026-08-04
 */
public enum SubscriptionTier {

    /** 7-day trial with full access — expires at AppUser.demoExpiresAt. */
    DEMO,

    /** Post-demo-expiration decision state — no app access until user chooses a path. */
    FREE_PENDING,

    /** Email-only tier — daily newsletter, no app login. */
    FREE,

    /** Paid tier — full newsletter, full archive, semantic search, higher query limits. */
    SUBSCRIBER,

    /** Enterprise API tier — highest query limits, 10 API keys, premium API access. */
    ENTERPRISE
}
