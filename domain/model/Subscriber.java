package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a newsletter subscriber.
 *
 * <p>Subscribers receive the weekly newsletter via email.  The {@code active} flag
 * allows soft-deactivation: a subscriber can be removed from the mailing list
 * without deleting the historical record.  {@code subscribedAt} records when
 * the subscription was created and is set once at registration time.
 *
 * <p>The {@code tier} field determines what content the subscriber receives.
 * Billing for the paid tier (SUBSCRIBER) is handled externally by Stripe Billing;
 * this field is updated via Stripe webhook events.
 *
 * <p>Email is the natural business key and serves as the deduplication identifier
 * in the persistence layer.  The domain does not enforce email-format validation
 * beyond a non-blank check; callers are responsible for providing well-formed
 * addresses.
 *
 * @param email                 The subscriber's email address; used as the unique key.
 *                              Must not be blank.
 * @param name                  The subscriber's display name.  Must not be blank.
 * @param active                {@code true} if the subscriber should receive mailings.
 * @param subscribedAt          Timestamp of when the subscription was created.
 * @param tier                  Subscription tier (FREE, SUBSCRIBER).
 *                              Defaults to {@link SubscriptionTier#FREE} if {@code null}.
 * @param unsubscribeToken      UUID token for one-click email unsubscribe links.  Nullable.
 * @param stripeCustomerId      Stripe customer ID from checkout session.  Nullable.
 * @param stripeSubscriptionId  Stripe subscription ID for portal management.  Nullable.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-04-13
 * @updated 2026-07-31
 */
public record Subscriber(
        String           email,
        String           name,
        boolean          active,
        Instant          subscribedAt,
        SubscriptionTier tier,
        String           unsubscribeToken,
        String           stripeCustomerId,
        String           stripeSubscriptionId
) {
    /** Compact canonical constructor — validates required fields, defaults tier to FREE. */
    public Subscriber {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (tier == null) {
            tier = SubscriptionTier.FREE;
        }
    }
}
