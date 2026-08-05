package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannelType;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookChannelPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link WebhookConfigController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Import(SecurityConfig.class)
@WebMvcTest(WebhookConfigController.class)
class WebhookConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookChannelPort webhookChannelPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    @DisplayName("GET /settings/webhooks renders page for authenticated user")
    @WithMockUser(username = "user@test.com")
    void webhooksPage_authenticated_rendersPage() throws Exception {
        WebhookChannel channel = new WebhookChannel("ch1", "user@test.com", "Slack",
                "https://hooks.slack.com/test", WebhookChannelType.SLACK, true,
                Set.of(WebhookEventType.WATCHLIST_MATCH), Instant.now());
        when(webhookChannelPort.findByOwnerEmail("user@test.com")).thenReturn(List.of(channel));

        mockMvc.perform(get("/settings/webhooks"))
                .andExpect(status().isOk())
                .andExpect(view().name("webhooks"))
                .andExpect(model().attributeExists("channels"))
                .andExpect(model().attributeExists("channelTypes"))
                .andExpect(model().attributeExists("eventTypes"));
    }

    @Test
    @DisplayName("GET /settings/webhooks with no channels shows empty state")
    @WithMockUser(username = "user@test.com")
    void webhooksPage_noChannels_showsEmptyState() throws Exception {
        when(webhookChannelPort.findByOwnerEmail("user@test.com")).thenReturn(List.of());

        mockMvc.perform(get("/settings/webhooks"))
                .andExpect(status().isOk())
                .andExpect(view().name("webhooks"));
    }

    @Test
    @DisplayName("GET /settings/webhooks unauthenticated redirects to login")
    void webhooksPage_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/settings/webhooks"))
                .andExpect(status().is3xxRedirection());
    }
}
