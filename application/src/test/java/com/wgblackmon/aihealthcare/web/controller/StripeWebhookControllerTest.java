package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice tests for {@link StripeWebhookController}.
 *
 * <p>Tests use a mock {@link StripeProperties} with a known test secret.
 * Valid Stripe-Signature headers are computed via HMAC-SHA256 so the
 * controller's signature verification passes.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-23
 * @updated 2026-08-06
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(controllers = {StripeWebhookController.class, GlobalExceptionHandler.class})
class StripeWebhookControllerTest {

    private static final String TEST_WEBHOOK_SECRET = "whsec_test_secret_for_unit_tests";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private StripeProperties stripeProperties;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private AppUserPort appUserPort;

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
    // Webhook secret not configured — should return 500
    // -------------------------------------------------------------------------

    @Test
    void webhook_noWebhookSecret_returns500() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getWebhookSecret()).thenReturn("");

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Webhook secret not configured"));

        verify(subscriberPort, never()).save(any());
    }

    @Test
    void webhook_nullWebhookSecret_returns500() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getWebhookSecret()).thenReturn(null);

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Webhook secret not configured"));

        verify(subscriberPort, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Invalid signature — should return 400
    // -------------------------------------------------------------------------

    @Test
    void webhook_invalidSignature_returns400() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getWebhookSecret()).thenReturn(TEST_WEBHOOK_SECRET);

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=12345,v1=invalidsignature")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid signature"));
    }

    // -------------------------------------------------------------------------
    // Unhandled event type — should return 200 but not modify subscribers
    // -------------------------------------------------------------------------

    @Test
    void webhook_unhandledEventType_returns200() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getWebhookSecret()).thenReturn(TEST_WEBHOOK_SECRET);

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
                        .header("Stripe-Signature", computeStripeSignature(payload))
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("Received"));

        verify(subscriberPort, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // subscription.deleted — returns 200
    // -------------------------------------------------------------------------

    @Test
    void webhook_subscriptionDeleted_returns200() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getWebhookSecret()).thenReturn(TEST_WEBHOOK_SECRET);

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
                        .header("Stripe-Signature", computeStripeSignature(payload))
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
        when(stripeProperties.getWebhookSecret()).thenReturn(TEST_WEBHOOK_SECRET);

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
                        .header("Stripe-Signature", computeStripeSignature(payload))
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("Received"));
    }

    @Test
    void webhook_checkoutCompleted_withAppUser_returns200() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getWebhookSecret()).thenReturn(TEST_WEBHOOK_SECRET);

        String payload = """
                {
                  "id": "evt_test_reenable",
                  "type": "checkout.session.completed",
                  "api_version": "2025-04-30.basil",
                  "data": {
                    "object": {
                      "id": "cs_test_456",
                      "object": "checkout.session",
                      "customer_email": "buyer@example.com"
                    }
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", computeStripeSignature(payload))
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("Received"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Computes a valid Stripe-Signature header for the given payload using
     * the test webhook secret.  Format: {@code t=<timestamp>,v1=<hmac>}.
     */
    private String computeStripeSignature(String payload) {
        long timestamp = Instant.now().getEpochSecond();
        String signedContent = timestamp + "." + payload;
        String hmac = hmacSha256(signedContent, TEST_WEBHOOK_SECRET);
        return "t=" + timestamp + ",v1=" + hmac;
    }

    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }
}
