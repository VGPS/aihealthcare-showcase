package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable payload sent to a webhook channel when a pipeline event occurs.
 *
 * <p>Contains the event metadata in a channel-agnostic format. The delivery
 * adapter formats this into the appropriate JSON structure for the target
 * channel type (Slack Block Kit, Teams Adaptive Card, or generic JSON).
 *
 * @param eventType  the type of event that triggered this notification
 * @param title      short headline (e.g. "3 New Watchlist Matches")
 * @param summary    one-paragraph event summary
 * @param detailUrl  link to the detail page on the app (nullable for PIPELINE_COMPLETE)
 * @param occurredAt when the event was detected
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public record WebhookPayload(
        WebhookEventType eventType,
        String title,
        String summary,
        String detailUrl,
        Instant occurredAt
) {

    public WebhookPayload {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
    }
}
