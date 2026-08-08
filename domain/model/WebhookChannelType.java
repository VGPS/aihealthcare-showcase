package com.wgblackmon.aihealthcare.domain.model;

/**
 * Delivery channel type for outbound webhook notifications.
 *
 * <p>Each type determines the JSON payload format sent to the webhook URL:
 * <ul>
 *   <li>{@code SLACK} — Slack incoming webhook (Block Kit JSON)</li>
 *   <li>{@code TEAMS} — Microsoft Teams incoming webhook (Adaptive Card)</li>
 *   <li>{@code CUSTOM} — Generic JSON payload for custom integrations</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public enum WebhookChannelType {
    SLACK,
    TEAMS,
    CUSTOM
}
