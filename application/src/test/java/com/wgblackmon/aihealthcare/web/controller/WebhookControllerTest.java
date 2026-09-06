package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannelType;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookChannelPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookNotificationPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link WebhookController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-09-06
 */
@Import(SecurityConfig.class)
@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookChannelPort webhookChannelPort;

    @MockitoBean
    private WebhookNotificationPort webhookNotificationPort;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    private void stubUser(String email, SubscriptionTier tier) {
        Subscriber subscriber = new Subscriber(email, "Test User", true, NOW, tier, null, null, null);
        when(subscriberPort.findByEmail(email)).thenReturn(Optional.of(subscriber));
    }

    private WebhookChannel testChannel(String id, String owner) {
        return new WebhookChannel(id, owner, "Test Channel",
                "https://hooks.slack.com/test",
                WebhookChannelType.SLACK, true,
                Set.of(WebhookEventType.WATCHLIST_MATCH), NOW);
    }

    @Test
    @DisplayName("POST /api/v1/webhooks creates channel for SUBSCRIBER")
    @WithMockUser(username = "sub@test.com")
    void createChannel_subscriber_returns201() throws Exception {
        stubUser("sub@test.com", SubscriptionTier.SUBSCRIBER);

        mockMvc.perform(post("/api/v1/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Slack\", \"webhookUrl\": \"https://hooks.slack.com/test\", \"channelType\": \"SLACK\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Slack"))
                .andExpect(jsonPath("$.channelType").value("SLACK"));

        verify(webhookChannelPort).save(any(WebhookChannel.class));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks denied for FREE tier")
    @WithMockUser(username = "free@test.com")
    void createChannel_freeTier_returns403() throws Exception {
        stubUser("free@test.com", SubscriptionTier.FREE);

        mockMvc.perform(post("/api/v1/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ch\", \"webhookUrl\": \"https://url\"}"))
                .andExpect(status().isForbidden());

        verify(webhookChannelPort, never()).save(any());
    }

    @Test
    @DisplayName("GET /api/v1/webhooks returns user's channels")
    @WithMockUser(username = "user@test.com")
    void listChannels_returnsUserChannels() throws Exception {
        when(webhookChannelPort.findByOwnerEmail("user@test.com"))
                .thenReturn(List.of(testChannel("ch1", "user@test.com")));

        mockMvc.perform(get("/api/v1/webhooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Channel"))
                .andExpect(jsonPath("$[0].channelType").value("SLACK"));
    }

    @Test
    @DisplayName("DELETE /api/v1/webhooks/{id} by owner returns 204")
    @WithMockUser(username = "user@test.com")
    void deleteChannel_byOwner_returns204() throws Exception {
        when(webhookChannelPort.findById("ch1"))
                .thenReturn(Optional.of(testChannel("ch1", "user@test.com")));

        mockMvc.perform(delete("/api/v1/webhooks/ch1"))
                .andExpect(status().isNoContent());

        verify(webhookChannelPort).deleteById("ch1");
    }

    @Test
    @DisplayName("DELETE /api/v1/webhooks/{id} by non-owner returns 403")
    @WithMockUser(username = "other@test.com")
    void deleteChannel_byNonOwner_returns403() throws Exception {
        when(webhookChannelPort.findById("ch1"))
                .thenReturn(Optional.of(testChannel("ch1", "user@test.com")));

        mockMvc.perform(delete("/api/v1/webhooks/ch1"))
                .andExpect(status().isForbidden());

        verify(webhookChannelPort, never()).deleteById("ch1");
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/{id}/test sends test notification")
    @WithMockUser(username = "user@test.com")
    void testChannel_sendsNotification() throws Exception {
        when(webhookChannelPort.findById("ch1"))
                .thenReturn(Optional.of(testChannel("ch1", "user@test.com")));
        when(webhookNotificationPort.send(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/ch1/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("delivered"));
    }
}
