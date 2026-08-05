package com.wgblackmon.aihealthcare.domain.model;

/**
 * Types of events that can trigger a webhook notification to a
 * subscriber's configured channel (Slack, Teams, or custom).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public enum WebhookEventType {
    WATCHLIST_MATCH,
    REGULATORY_ALERT,
    TREND_ALERT,
    PIPELINE_COMPLETE
}
