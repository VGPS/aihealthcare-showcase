package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link IntelligenceConsoleController}.
 *
 * <p>Tests admin access gating and page rendering. POST endpoints that
 * proxy to the Claude service are not tested here since the service
 * would need to be running.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-08
 * @updated 2026-08-08
 */
@Import(SecurityConfig.class)
@WebMvcTest(IntelligenceConsoleController.class)
class IntelligenceConsoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    @WithMockUser(roles = "ADMIN")
    void console_adminAccess_returnsOk() throws Exception {
        mockMvc.perform(get("/admin/intelligence"))
                .andExpect(status().isOk())
                .andExpect(view().name("intelligence-console"))
                .andExpect(model().attributeExists("baseUrl"))
                .andExpect(model().attribute("activeTab", "coding"));
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
}
