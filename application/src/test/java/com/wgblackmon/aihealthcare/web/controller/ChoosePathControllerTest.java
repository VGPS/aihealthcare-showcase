package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link ChoosePathController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
@Import(SecurityConfig.class)
@WebMvcTest(ChoosePathController.class)
class ChoosePathControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private StripeProperties stripeProperties;

    @Test
    void getChoosePath_noAuth_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/choose-path"))
                .andExpect(status().isOk())
                .andExpect(view().name("choose-path"));
    }

    @Test
    @WithMockUser(username = "demo@example.com")
    void getChoosePath_rendersDecisionPage() throws Exception {
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getSubscriberPriceId()).thenReturn("price_123");

        mockMvc.perform(get("/choose-path"))
                .andExpect(status().isOk())
                .andExpect(view().name("choose-path"))
                .andExpect(content().string(containsString("Your Demo Has Expired")));
    }

    @Test
    @WithMockUser(username = "demo@example.com")
    void postChoosePath_free_disablesUserAndRedirects() throws Exception {
        AppUser user = new AppUser("demo@example.com", "hash", "Demo", "USER", true,
                SubscriptionTier.FREE_PENDING, Instant.now());
        when(appUserPort.findByEmail("demo@example.com")).thenReturn(Optional.of(user));
        Subscriber sub = new Subscriber("demo@example.com", "Demo", true,
                Instant.now(), SubscriptionTier.FREE_PENDING);
        when(subscriberPort.findByEmail("demo@example.com")).thenReturn(Optional.of(sub));

        mockMvc.perform(post("/choose-path")
                        .with(csrf())
                        .param("choice", "free"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?demo-expired"));

        verify(appUserPort).save(org.mockito.ArgumentMatchers.argThat(
                u -> !u.enabled() && u.tier() == SubscriptionTier.FREE));
    }

    @Test
    @WithMockUser(username = "demo@example.com")
    void postChoosePath_subscriber_redirectsToPricing() throws Exception {
        mockMvc.perform(post("/choose-path")
                        .with(csrf())
                        .param("choice", "subscriber"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pricing"));
    }
}
