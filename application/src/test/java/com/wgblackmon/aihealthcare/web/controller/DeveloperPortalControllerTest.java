package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link DeveloperPortalController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-09-06
 */
@Import(SecurityConfig.class)
@WebMvcTest(DeveloperPortalController.class)
class DeveloperPortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private UsageTrackingPort usageTrackingPort;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockBean
    private TierResolver tierResolver;

    private void stubUser(String email, SubscriptionTier tier) {
        when(tierResolver.resolveTier(any(Principal.class))).thenReturn(tier);
        when(tierResolver.isAdmin(any())).thenReturn(false);
        Subscriber subscriber = new Subscriber(email, "Test User", true, Instant.now(), tier, null, null, null);
        when(subscriberPort.findByEmail(email)).thenReturn(Optional.of(subscriber));
        when(apiKeyPort.findAllByOwnerEmail(email)).thenReturn(List.of());
        when(usageTrackingPort.getOrCreateUsage(anyString(), anyString()))
                .thenReturn(new UsageRecord(email, "2026-08", 10, 200));
    }

    @Test
    @WithMockUser(username = "sub@test.com")
    void developer_authenticatedSubscriber_showsKeysAndUsage() throws Exception {
        stubUser("sub@test.com", SubscriptionTier.SUBSCRIBER);

        mockMvc.perform(get("/developer"))
                .andExpect(status().isOk())
                .andExpect(view().name("developer"))
                .andExpect(model().attribute("authenticated", true))
                .andExpect(model().attribute("canCreateKeys", true))
                .andExpect(model().attribute("maxKeys", 3))
                .andExpect(model().attribute("tier", "SUBSCRIBER"));
    }

    @Test
    @WithMockUser(username = "ent@test.com")
    void developer_enterpriseUser_showsHigherKeyLimit() throws Exception {
        stubUser("ent@test.com", SubscriptionTier.ENTERPRISE);

        mockMvc.perform(get("/developer"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canCreateKeys", true))
                .andExpect(model().attribute("maxKeys", 10))
                .andExpect(model().attribute("tier", "ENTERPRISE"));
    }

    @Test
    @WithMockUser(username = "free@test.com")
    void developer_freeUser_cannotCreateKeys() throws Exception {
        stubUser("free@test.com", SubscriptionTier.FREE);

        mockMvc.perform(get("/developer"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canCreateKeys", false))
                .andExpect(model().attribute("tier", "FREE"));
    }

    @Test
    void developer_unauthenticated_showsDocsOnly() throws Exception {
        mockMvc.perform(get("/developer"))
                .andExpect(status().isOk())
                .andExpect(view().name("developer"))
                .andExpect(model().attribute("authenticated", false));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void developer_admin_canCreateUnlimitedKeys() throws Exception {
        stubUser("admin@test.com", SubscriptionTier.SUBSCRIBER);
        when(tierResolver.isAdmin(any())).thenReturn(true);

        mockMvc.perform(get("/developer"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canCreateKeys", true))
                .andExpect(model().attribute("isAdmin", true))
                .andExpect(model().attribute("maxKeys", 100));
    }
}
