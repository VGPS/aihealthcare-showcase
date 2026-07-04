package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice tests for {@link StripeWebhookController}.
 *
 * <p>Tests use a mock {@link StripeProperties} so no real Stripe API key is
 * needed.  Signature verification is skipped (webhook secret left blank) to
 * allow testing with raw JSON payloads.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-23
 * @updated 2026-05-23
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(controllers = {StripeWebhookController.class, GlobalExceptionHandler.class})
class StripeWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private StripeProperties stripeProperties;

    @MockitoBean
    private SubscriberPort subscriberPort;

    // -------------------------------------------------------------------------
    // Stripe not configured
    // -------------------------------------------------------------------------

    @Test
    void webhook_stripeNotConfigured_returns503() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Stripe integration not configured"));

        verify(subscriberPort, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Unhandled event type — should return 200 but not modify subscribers
    // -------------------------------------------------------------------------

    @Test
    void webhook_unhandledEventType_returns200() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getWebhookSecret()).thenReturn("");

        String payload = """
                {
                  "id": "evt_test_123",
                  "type": "payment_intent.succeeded",
                  "data": {
                    "object": {}
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("Received"));

        verify(subscriberPort, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // subscription.deleted — returns 200 (deserialization is handled gracefully)
    // -------------------------------------------------------------------------

    @Test
    void webhook_subscriptionDeleted_returns200() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getWebhookSecret()).thenReturn("");

        String payload = """
                {
                  "id": "evt_test_del",
                  "type": "customer.subscription.deleted",
                  "api_version": "2025-04-30.basil",
                  "data": {
                    "object": {
                      "id": "sub_123",
                      "object": "subscription",
                      "metadata": {
                        "customer_email": "paid@example.com"
                      }
                    }
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("Received"));
    }

    // -------------------------------------------------------------------------
    // checkout.session.completed — returns 200
    // -------------------------------------------------------------------------

    @Test
    void webhook_checkoutCompleted_returns200() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getWebhookSecret()).thenReturn("");

        String payload = """
                {
                  "id": "evt_test_checkout",
                  "type": "checkout.session.completed",
                  "api_version": "2025-04-30.basil",
                  "data": {
                    "object": {
                      "id": "cs_test_123",
                      "object": "checkout.session",
                      "customer_email": "buyer@example.com"
                    }
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("Received"));
    }
}
