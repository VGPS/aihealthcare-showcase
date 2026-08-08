package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookPayload;

/**
 * Outbound port for sending webhook notification payloads to external
 * channels (Slack, Teams, custom endpoints).
 *
 * <p>Implementations format the {@link WebhookPayload} into the appropriate
 * JSON structure for the target {@link WebhookChannel#channelType()} and
 * POST it to the channel's webhook URL.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface WebhookNotificationPort {

    /**
     * Sends a notification payload to the given webhook channel.
     *
     * @param channel the target channel with URL and type
     * @param payload the event payload to deliver
     * @return true if delivery succeeded, false otherwise
     */
    boolean send(WebhookChannel channel, WebhookPayload payload);
}
