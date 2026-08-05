package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannelType;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.model.WebhookPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WebhookNotificationAdapter}.
 *
 * <p>Tests that delivery to invalid URLs fails gracefully (returns false)
 * rather than throwing exceptions. Format tests verify the adapter
 * handles all channel types without errors.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
class WebhookNotificationAdapterTest {

    private WebhookNotificationAdapter adapter;
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        adapter = new WebhookNotificationAdapter();
    }

    private WebhookChannel channel(String id, WebhookChannelType type, String url) {
        return new WebhookChannel(id, "user@test.com", "Test Channel", url,
                type, true, Set.of(WebhookEventType.WATCHLIST_MATCH), NOW);
    }

    private WebhookPayload payload() {
        return new WebhookPayload(
                WebhookEventType.WATCHLIST_MATCH,
                "Test Alert",
                "This is a test notification.",
                "/watchlist",
                NOW
        );
    }

    @Test
    void send_invalidUrl_returnsFalse() {
        WebhookChannel ch = channel("ch1", WebhookChannelType.SLACK, "https://invalid.nonexistent.test/webhook");
        boolean result = adapter.send(ch, payload());
        assertThat(result).isFalse();
    }

    @Test
    void send_teamsInvalidUrl_returnsFalse() {
        WebhookChannel ch = channel("ch2", WebhookChannelType.TEAMS, "https://invalid.nonexistent.test/teams");
        boolean result = adapter.send(ch, payload());
        assertThat(result).isFalse();
    }

    @Test
    void send_customInvalidUrl_returnsFalse() {
        WebhookChannel ch = channel("ch3", WebhookChannelType.CUSTOM, "https://invalid.nonexistent.test/custom");
        boolean result = adapter.send(ch, payload());
        assertThat(result).isFalse();
    }

    @Test
    void send_nullDetailUrl_doesNotThrow() {
        WebhookChannel ch = channel("ch4", WebhookChannelType.SLACK, "https://invalid.nonexistent.test/slack");
        WebhookPayload noDetail = new WebhookPayload(
                WebhookEventType.PIPELINE_COMPLETE, "Done", "All pipelines complete.", null, NOW);
        boolean result = adapter.send(ch, noDetail);
        assertThat(result).isFalse();
    }
}
