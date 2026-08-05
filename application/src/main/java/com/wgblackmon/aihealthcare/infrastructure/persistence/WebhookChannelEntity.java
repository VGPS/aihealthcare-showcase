package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code webhook_channels} table.
 *
 * <p>Maps to the immutable {@link com.wgblackmon.aihealthcare.domain.model.WebhookChannel}
 * domain record. The {@code subscribedEvents} field is stored as a pipe-delimited
 * string (e.g. "WATCHLIST_MATCH|REGULATORY_ALERT").
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Entity
@Table(name = "webhook_channels")
public class WebhookChannelEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "owner_email", nullable = false, length = 255)
    private String ownerEmail;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "webhook_url", nullable = false, length = 2048)
    private String webhookUrl;

    @Column(name = "channel_type", nullable = false, length = 20)
    private String channelType;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "subscribed_events", nullable = false, length = 500)
    private String subscribedEvents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public WebhookChannelEntity() {}

    public String getId()                                    { return id; }
    public void setId(String id)                             { this.id = id; }

    public String getOwnerEmail()                            { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail)             { this.ownerEmail = ownerEmail; }

    public String getName()                                  { return name; }
    public void setName(String name)                         { this.name = name; }

    public String getWebhookUrl()                            { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl)             { this.webhookUrl = webhookUrl; }

    public String getChannelType()                           { return channelType; }
    public void setChannelType(String channelType)           { this.channelType = channelType; }

    public boolean isActive()                                { return active; }
    public void setActive(boolean active)                    { this.active = active; }

    public String getSubscribedEvents()                      { return subscribedEvents; }
    public void setSubscribedEvents(String subscribedEvents) { this.subscribedEvents = subscribedEvents; }

    public Instant getCreatedAt()                            { return createdAt; }
    public void setCreatedAt(Instant createdAt)              { this.createdAt = createdAt; }
}
