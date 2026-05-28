package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice tests for {@link StripeCheckoutController}.
 *
 * <p>Tests verify guard conditions (Stripe not configured, missing fields).
 * The happy path requires a live Stripe API key and is tested manually or
 * in an integration test with {@code @Profile("stripe-integration")}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-23
 * @updated 2026-05-23
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(controllers = {StripeCheckoutController.class, GlobalExceptionHandler.class})
class StripeCheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StripeProperties stripeProperties;

    // -------------------------------------------------------------------------
    // Stripe not configured
    // -------------------------------------------------------------------------

    @Test
    void checkout_stripeNotConfigured_returns503() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/v1/stripe/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"priceId\":\"price_123\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Stripe integration not configured"));
    }

    // -------------------------------------------------------------------------
    // Missing email
    // -------------------------------------------------------------------------

    @Test
    void checkout_missingEmail_returns400() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);

        mockMvc.perform(post("/api/v1/stripe/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"priceId\":\"price_123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("email is required"));
    }

    // -------------------------------------------------------------------------
    // Missing priceId
    // -------------------------------------------------------------------------

    @Test
    void checkout_missingPriceId_returns400() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);

        mockMvc.perform(post("/api/v1/stripe/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"priceId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("priceId is required"));
    }
}
