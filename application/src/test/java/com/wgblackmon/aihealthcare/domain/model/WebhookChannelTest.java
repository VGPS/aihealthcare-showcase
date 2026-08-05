package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link WebhookChannel}, {@link WebhookPayload},
 * {@link WebhookChannelType}, and {@link WebhookEventType} domain records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
class WebhookChannelTest {

    private static final Instant NOW = Instant.now();

    @Test
    void validChannel_createsSuccessfully() {
        WebhookChannel channel = new WebhookChannel(
                "ch1", "user@test.com", "#alerts",
                "https://hooks.slack.com/services/T/B/x",
                WebhookChannelType.SLACK, true,
                Set.of(WebhookEventType.WATCHLIST_MATCH), NOW
        );

        assertThat(channel.id()).isEqualTo("ch1");
        assertThat(channel.ownerEmail()).isEqualTo("user@test.com");
        assertThat(channel.channelType()).isEqualTo(WebhookChannelType.SLACK);
        assertThat(channel.subscribedEvents()).containsExactly(WebhookEventType.WATCHLIST_MATCH);
    }

    @Test
    void blankId_throws() {
        assertThatThrownBy(() -> new WebhookChannel(
                "", "user@test.com", "name", "https://url",
                WebhookChannelType.SLACK, true,
                Set.of(WebhookEventType.WATCHLIST_MATCH), NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyEvents_throws() {
        assertThatThrownBy(() -> new WebhookChannel(
                "ch1", "user@test.com", "name", "https://url",
                WebhookChannelType.SLACK, true,
                Set.of(), NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multipleEventTypes_allStored() {
        WebhookChannel channel = new WebhookChannel(
                "ch1", "user@test.com", "full-alerts", "https://url",
                WebhookChannelType.TEAMS, true,
                Set.of(WebhookEventType.WATCHLIST_MATCH, WebhookEventType.REGULATORY_ALERT,
                       WebhookEventType.TREND_ALERT, WebhookEventType.PIPELINE_COMPLETE), NOW
        );

        assertThat(channel.subscribedEvents()).hasSize(4);
    }

    @Test
    void validPayload_createsSuccessfully() {
        WebhookPayload payload = new WebhookPayload(
                WebhookEventType.REGULATORY_ALERT,
                "New FDA Alert",
                "3 new 510(k) clearances detected.",
                "/dashboard/regulatory",
                NOW
        );

        assertThat(payload.eventType()).isEqualTo(WebhookEventType.REGULATORY_ALERT);
        assertThat(payload.title()).isEqualTo("New FDA Alert");
        assertThat(payload.detailUrl()).isEqualTo("/dashboard/regulatory");
    }

    @Test
    void payload_nullDetailUrl_allowed() {
        WebhookPayload payload = new WebhookPayload(
                WebhookEventType.PIPELINE_COMPLETE,
                "Pipeline Done",
                "All pipelines completed.",
                null,
                NOW
        );

        assertThat(payload.detailUrl()).isNull();
    }
}
