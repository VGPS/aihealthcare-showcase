package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookPayload;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * REST-based adapter implementing {@link WebhookNotificationPort}.
 *
 * <p>Formats the {@link WebhookPayload} into the appropriate JSON structure
 * for the target channel type and POSTs it to the webhook URL:
 * <ul>
 *   <li><b>SLACK</b> — Slack Block Kit JSON with section blocks</li>
 *   <li><b>TEAMS</b> — Microsoft Teams MessageCard format</li>
 *   <li><b>CUSTOM</b> — Generic JSON payload</li>
 * </ul>
 *
 * <p>Delivery failures are logged but never propagated — webhook
 * notification is best-effort.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class WebhookNotificationAdapter implements WebhookNotificationPort {

    private final RestClient restClient;

    public WebhookNotificationAdapter() {
        log.debug("WebhookNotificationAdapter()");
        this.restClient = RestClient.create();
    }

    @Override
    public boolean send(WebhookChannel channel, WebhookPayload payload) {
        log.debug("send() | channelId={}, channelType={}, eventType={}",
                  channel.id(), channel.channelType(), payload.eventType());
        try {
            String json = formatPayload(channel, payload);
            restClient.post()
                    .uri(channel.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
            log.info("send() | webhook delivered to {} ({})", channel.name(), channel.channelType());
            log.debug("send() | return=true");
            return true;
        } catch (Exception e) {
            log.warn("send() | webhook delivery failed for channel '{}': {}", channel.name(), e.getMessage());
            log.debug("send() | return=false");
            return false;
        }
    }

    private String formatPayload(WebhookChannel channel, WebhookPayload payload) {
        log.debug("formatPayload() | channelType={}", channel.channelType());
        String result;
        switch (channel.channelType()) {
            case SLACK:
                result = formatSlackPayload(payload);
                break;
            case TEAMS:
                result = formatTeamsPayload(payload);
                break;
            default:
                result = formatCustomPayload(payload);
                break;
        }
        log.debug("formatPayload() | return=json[{}chars]", result.length());
        return result;
    }

    private String formatSlackPayload(WebhookPayload payload) {
        log.debug("formatSlackPayload() | title={}", payload.title());
        String detailLink = payload.detailUrl() != null
                ? ",{\"type\":\"section\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"<" + escapeJson(payload.detailUrl()) + "|View Details>\"}}"
                : "";
        String result = "{\"blocks\":["
                + "{\"type\":\"header\",\"text\":{\"type\":\"plain_text\",\"text\":\"" + escapeJson(payload.title()) + "\"}},"
                + "{\"type\":\"section\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"" + escapeJson(payload.summary()) + "\"}}"
                + detailLink
                + "]}";
        log.debug("formatSlackPayload() | return=json[{}chars]", result.length());
        return result;
    }

    private String formatTeamsPayload(WebhookPayload payload) {
        log.debug("formatTeamsPayload() | title={}", payload.title());
        String detailAction = payload.detailUrl() != null
                ? ",\"potentialAction\":[{\"@type\":\"OpenUri\",\"name\":\"View Details\",\"targets\":[{\"os\":\"default\",\"uri\":\"" + escapeJson(payload.detailUrl()) + "\"}]}]"
                : "";
        String result = "{\"@type\":\"MessageCard\",\"@context\":\"http://schema.org/extensions\","
                + "\"themeColor\":\"0076D7\","
                + "\"summary\":\"" + escapeJson(payload.title()) + "\","
                + "\"sections\":[{\"activityTitle\":\"" + escapeJson(payload.title()) + "\","
                + "\"text\":\"" + escapeJson(payload.summary()) + "\","
                + "\"facts\":[{\"name\":\"Event\",\"value\":\"" + payload.eventType().name() + "\"}]}]"
                + detailAction
                + "}";
        log.debug("formatTeamsPayload() | return=json[{}chars]", result.length());
        return result;
    }

    private String formatCustomPayload(WebhookPayload payload) {
        log.debug("formatCustomPayload() | title={}", payload.title());
        String result = "{\"eventType\":\"" + payload.eventType().name() + "\","
                + "\"title\":\"" + escapeJson(payload.title()) + "\","
                + "\"summary\":\"" + escapeJson(payload.summary()) + "\","
                + "\"detailUrl\":" + (payload.detailUrl() != null ? "\"" + escapeJson(payload.detailUrl()) + "\"" : "null") + ","
                + "\"occurredAt\":\"" + payload.occurredAt() + "\"}";
        log.debug("formatCustomPayload() | return=json[{}chars]", result.length());
        return result;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }
}
