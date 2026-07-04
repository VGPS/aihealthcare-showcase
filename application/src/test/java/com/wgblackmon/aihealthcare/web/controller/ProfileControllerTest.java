package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import com.wgblackmon.aihealthcare.infrastructure.config.TierLimitProperties;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link ProfileController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
@Import(SecurityConfig.class)
@WebMvcTest(ProfileController.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private UsageTrackingPort usageTrackingPort;

    @MockitoBean
    private TierLimitProperties tierLimitProperties;

    @MockitoBean
    private StripeProperties stripeProperties;

    private void stubDefaults(String email, SubscriptionTier tier) {
        Subscriber sub = new Subscriber(email, "Test User", true, Instant.now(), tier);
        when(subscriberPort.findByEmail(email)).thenReturn(Optional.of(sub));
        UsageRecord usage = new UsageRecord(email, "2026-07", 12, 200);
        when(usageTrackingPort.getOrCreateUsage(eq(email), anyString())).thenReturn(usage);

        TierLimitProperties.TierConfig config = new TierLimitProperties.TierConfig();
        config.setArchiveDays(tier == SubscriptionTier.MEMBER ? 0 : 30);
        config.setMonthlyQueryLimit(tier == SubscriptionTier.MEMBER ? 200 : 15);
        when(tierLimitProperties.getFree()).thenReturn(config);
        when(tierLimitProperties.getMember()).thenReturn(config);
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getMemberPriceId()).thenReturn("price_member_123");
    }

    @Test
    void profile_noAuth_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void profile_authenticatedUser_rendersProfile() throws Exception {
        stubDefaults("user@example.com", SubscriptionTier.FREE);

        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attribute("email", "user@example.com"))
                .andExpect(model().attribute("tier", "FREE"));
    }

    @Test
    @WithMockUser(username = "member@example.com")
    void profile_memberUser_showsMemberTier() throws Exception {
        stubDefaults("member@example.com", SubscriptionTier.MEMBER);

        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tier", "MEMBER"))
                .andExpect(content().string(containsString("MEMBER")));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void profile_showsUsageStats() throws Exception {
        stubDefaults("user@example.com", SubscriptionTier.FREE);

        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("queriesUsed", 12))
                .andExpect(model().attribute("queryLimit", 200));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void profile_adminUser_showsMemberTier() throws Exception {
        when(subscriberPort.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        UsageRecord usage = new UsageRecord("admin@example.com", "2026-07", 0, 200);
        when(usageTrackingPort.getOrCreateUsage(eq("admin@example.com"), anyString())).thenReturn(usage);
        TierLimitProperties.TierConfig config = new TierLimitProperties.TierConfig();
        config.setArchiveDays(0);
        config.setMonthlyQueryLimit(200);
        when(tierLimitProperties.getMember()).thenReturn(config);
        when(stripeProperties.isEnabled()).thenReturn(true);

        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tier", "MEMBER"))
                .andExpect(model().attribute("isAdmin", true));
    }
}
