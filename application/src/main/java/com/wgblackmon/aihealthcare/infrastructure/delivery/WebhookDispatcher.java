package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.model.WebhookPayload;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookChannelPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates webhook notification delivery by finding all active channels
 * subscribed to a given event type and dispatching payloads to each.
 *
 * <p>Used by schedulers to send notifications without knowing about
 * individual channels. Each delivery is try-catch isolated so one
 * failing channel never blocks others.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class WebhookDispatcher {

    private final WebhookChannelPort channelPort;
    private final WebhookNotificationPort notificationPort;

    public WebhookDispatcher(WebhookChannelPort channelPort,
                             WebhookNotificationPort notificationPort) {
        log.debug("WebhookDispatcher() | channelPort={}, notificationPort={}",
                  channelPort.getClass().getSimpleName(),
                  notificationPort.getClass().getSimpleName());
        this.channelPort = channelPort;
        this.notificationPort = notificationPort;
    }

    /**
     * Dispatches a notification to all active channels subscribed to the given event type.
     *
     * @param eventType the event type to dispatch
     * @param title     short headline for the notification
     * @param summary   one-paragraph event summary
     * @param detailUrl link to the detail page (nullable)
     * @return number of channels successfully notified
     */
    public int dispatch(WebhookEventType eventType, String title, String summary, String detailUrl) {
        log.debug("dispatch() | eventType={}, title={}", eventType, title);

        List<WebhookChannel> channels = channelPort.findActiveByEventType(eventType);
        if (channels.isEmpty()) {
            log.debug("dispatch() | no active channels for eventType={}", eventType);
            log.debug("dispatch() | return=0");
            return 0;
        }

        WebhookPayload payload = new WebhookPayload(eventType, title, summary, detailUrl, Instant.now());
        int successCount = 0;

        for (WebhookChannel channel : channels) {
            try {
                boolean sent = notificationPort.send(channel, payload);
                if (sent) {
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("dispatch() | failed to notify channel '{}': {}", channel.name(), e.getMessage());
            }
        }

        log.info("dispatch() | {} notified {} of {} channels for {}",
                 eventType, successCount, channels.size(), title);
        log.debug("dispatch() | return={}", successCount);
        return successCount;
    }
}
