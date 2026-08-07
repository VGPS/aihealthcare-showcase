package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code subscribers} table.
 *
 * <p>Mirrors the immutable {@link com.wgblackmon.aihealthcare.domain.model.Subscriber}
 * domain record.  All mapping between the two types is performed inside
 * {@link SubscriberAdapter} — this entity never escapes into the domain or
 * application layers.
 *
 * <p>Email is the natural business key and serves as the primary key; no surrogate
 * ID is needed.  The {@code active} flag supports soft-deactivation without
 * deleting the historical record.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-04-13
 * @updated 2026-08-07
 */
@Entity
@Table(name = "subscribers")
public class SubscriberEntity {

    @Id
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "subscribed_at")
    private Instant subscribedAt;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier = "FREE";

    @Column(name = "unsubscribe_token", length = 36, unique = true, nullable = false)
    private String unsubscribeToken;

    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    /** Required no-arg constructor for JPA. */
    public SubscriberEntity() {}

    public String getEmail()              { return email; }
    public void setEmail(String email)    { this.email = email; }

    public String getName()               { return name; }
    public void setName(String name)      { this.name = name; }

    public boolean isActive()             { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getSubscribedAt()                   { return subscribedAt; }
    public void setSubscribedAt(Instant subscribedAt)  { this.subscribedAt = subscribedAt; }

    public String getTier()             { return tier; }
    public void setTier(String tier)    { this.tier = tier; }

    public String getUnsubscribeToken()                          { return unsubscribeToken; }
    public void setUnsubscribeToken(String unsubscribeToken)     { this.unsubscribeToken = unsubscribeToken; }

    public String getStripeCustomerId()                          { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId)     { this.stripeCustomerId = stripeCustomerId; }

    public String getStripeSubscriptionId()                              { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId)     { this.stripeSubscriptionId = stripeSubscriptionId; }
}
