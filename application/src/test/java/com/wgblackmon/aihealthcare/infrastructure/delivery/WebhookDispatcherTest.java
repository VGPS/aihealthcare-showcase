package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannelType;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.model.WebhookPayload;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookChannelPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookNotificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebhookDispatcher}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@ExtendWith(MockitoExtension.class)
class WebhookDispatcherTest {

    @Mock
    private WebhookChannelPort channelPort;

    @Mock
    private WebhookNotificationPort notificationPort;

    private WebhookDispatcher dispatcher;
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        dispatcher = new WebhookDispatcher(channelPort, notificationPort);
    }

    private WebhookChannel channel(String id) {
        return new WebhookChannel(id, "user@test.com", "Ch " + id,
                "https://hooks.example.com/" + id,
                WebhookChannelType.SLACK, true,
                Set.of(WebhookEventType.WATCHLIST_MATCH), NOW);
    }

    @Test
    void dispatch_noChannels_returnsZero() {
        when(channelPort.findActiveByEventType(WebhookEventType.WATCHLIST_MATCH))
                .thenReturn(List.of());

        int result = dispatcher.dispatch(WebhookEventType.WATCHLIST_MATCH,
                "Title", "Summary", "/detail");

        assertThat(result).isEqualTo(0);
        verify(notificationPort, never()).send(any(), any());
    }

    @Test
    void dispatch_twoChannels_sendsToAll() {
        WebhookChannel ch1 = channel("ch1");
        WebhookChannel ch2 = channel("ch2");
        when(channelPort.findActiveByEventType(WebhookEventType.WATCHLIST_MATCH))
                .thenReturn(List.of(ch1, ch2));
        when(notificationPort.send(any(), any())).thenReturn(true);

        int result = dispatcher.dispatch(WebhookEventType.WATCHLIST_MATCH,
                "3 Matches", "Found 3 new matches.", "/watchlist");

        assertThat(result).isEqualTo(2);
        verify(notificationPort).send(eq(ch1), any(WebhookPayload.class));
        verify(notificationPort).send(eq(ch2), any(WebhookPayload.class));
    }

    @Test
    void dispatch_oneFailsOneSucceeds_returnsOne() {
        WebhookChannel ch1 = channel("ch1");
        WebhookChannel ch2 = channel("ch2");
        when(channelPort.findActiveByEventType(WebhookEventType.REGULATORY_ALERT))
                .thenReturn(List.of(ch1, ch2));
        when(notificationPort.send(eq(ch1), any())).thenReturn(true);
        when(notificationPort.send(eq(ch2), any())).thenReturn(false);

        int result = dispatcher.dispatch(WebhookEventType.REGULATORY_ALERT,
                "Alert", "Regulatory alert", "/dashboard/regulatory");

        assertThat(result).isEqualTo(1);
    }

    @Test
    void dispatch_sendThrows_continuesToNextChannel() {
        WebhookChannel ch1 = channel("ch1");
        WebhookChannel ch2 = channel("ch2");
        when(channelPort.findActiveByEventType(WebhookEventType.TREND_ALERT))
                .thenReturn(List.of(ch1, ch2));
        when(notificationPort.send(eq(ch1), any())).thenThrow(new RuntimeException("Network error"));
        when(notificationPort.send(eq(ch2), any())).thenReturn(true);

        int result = dispatcher.dispatch(WebhookEventType.TREND_ALERT,
                "Trends", "New trends detected", "/dashboard/trends");

        assertThat(result).isEqualTo(1);
    }
}
