package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link IntelligenceConsoleController}.
 *
 * <p>Tests admin access gating, page rendering, and generic proxy endpoints.
 * Proxy POST/GET tests expect 502 since the Claude service is not running
 * during unit tests.
 *
 * @author  Bill Blackmon
 * @version 3.0
 * @since   2026-08-08
 * @updated 2026-08-14
 */
@Import(SecurityConfig.class)
@WebMvcTest(IntelligenceConsoleController.class)
@TestPropertySource(properties = "claude.intelligence.base-url=http://localhost:19999")
class IntelligenceConsoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private TierGatingService tierGatingService;

    @MockitoBean
    private UsageTrackingPort usageTrackingPort;

    @Test
    @WithMockUser(roles = "ADMIN")
    void console_adminAccess_returnsOk() throws Exception {
        mockMvc.perform(get("/admin/intelligence"))
                .andExpect(status().isOk())
                .andExpect(view().name("intelligence-console"))
                .andExpect(model().attributeExists("baseUrl"))
                .andExpect(model().attribute("activeTab", "chat"))
                .andExpect(model().attribute("isEnterprise", true))
                .andExpect(model().attribute("isSubscriber", true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void console_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/admin/intelligence"))
                .andExpect(status().isForbidden());
    }

    @Test
    void console_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/intelligence"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void proxyPost_adminAccess_returns502WhenServiceDown() throws Exception {
        mockMvc.perform(post("/admin/intelligence/api/v1/intelligence/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"test\"}"))
                .andExpect(status().is(502));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void proxyGet_adminAccess_returns502WhenServiceDown() throws Exception {
        mockMvc.perform(get("/admin/intelligence/api/v1/intelligence/synthesis/reports?days=7")
                        .with(csrf()))
                .andExpect(status().is(502));
    }

    @Test
    @WithMockUser(roles = "USER")
    void proxyPost_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/admin/intelligence/api/v1/intelligence/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"test\"}"))
                .andExpect(status().isForbidden());
    }
}
