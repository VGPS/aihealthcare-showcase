package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice tests for {@link StripePortalController}.
 *
 * <p>Tests verify guard conditions (Stripe not configured, no customer ID).
 * The happy path requires a live Stripe API key and is tested manually.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-31
 * @updated 2026-07-31
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(controllers = {StripePortalController.class, GlobalExceptionHandler.class})
class StripePortalControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;
    @MockitoBean
    private StripeProperties stripeProperties;
    @MockitoBean
    private SubscriberPort subscriberPort;

    @Test
    void portal_stripeNotConfigured_returns503() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/v1/stripe/portal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Stripe is not configured"));
    }

    @Test
    void portal_missingEmail_returns400() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);

        mockMvc.perform(post("/api/v1/stripe/portal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("email is required"));
    }

    @Test
    void portal_noStripeCustomerId_returns404() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);
        Subscriber sub = new Subscriber("test@example.com", "Test", true,
                Instant.now(), SubscriptionTier.SUBSCRIBER, null, null, null);
        when(subscriberPort.findByEmail("test@example.com")).thenReturn(Optional.of(sub));

        mockMvc.perform(post("/api/v1/stripe/portal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No Stripe subscription found for this account"));
    }
}
