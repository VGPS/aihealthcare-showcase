package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.Set;

/**
 * Subscriber-configured webhook channel for receiving push notifications
 * when pipeline events occur (watchlist matches, regulatory alerts, etc.).
 *
 * <p>Each channel points to an external webhook URL (Slack incoming webhook,
 * Teams connector, or custom endpoint) and subscribes to one or more
 * {@link WebhookEventType}s.
 *
 * @param id               unique identifier (UUID)
 * @param ownerEmail       email of the subscriber who owns this channel
 * @param name             display name (e.g. "#ai-alerts channel")
 * @param webhookUrl       the external webhook URL to POST notifications to
 * @param channelType      delivery format (SLACK, TEAMS, CUSTOM)
 * @param active           whether this channel is currently active
 * @param subscribedEvents event types this channel receives notifications for
 * @param createdAt        when this channel was configured
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public record WebhookChannel(
        String id,
        String ownerEmail,
        String name,
        String webhookUrl,
        WebhookChannelType channelType,
        boolean active,
        Set<WebhookEventType> subscribedEvents,
        Instant createdAt
) {

    public WebhookChannel {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("ownerEmail must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("webhookUrl must not be blank");
        }
        if (channelType == null) {
            throw new IllegalArgumentException("channelType must not be null");
        }
        if (subscribedEvents == null || subscribedEvents.isEmpty()) {
            throw new IllegalArgumentException("subscribedEvents must not be empty");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
