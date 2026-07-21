package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link LoginController}.
 *
 * <p>The login page is {@code permitAll} so no {@code @WithMockUser} is needed.
 * {@link SecurityConfig} is imported to apply the custom filter chain.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-28
 * @updated 2026-07-20
 */
@Import(SecurityConfig.class)
@WebMvcTest(LoginController.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    void login_returns200() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void login_withError_showsErrorMessage() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invalid email or password")));
    }

    @Test
    void login_withLogout_showsLogoutMessage() throws Exception {
        mockMvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("signed out successfully")));
    }

    @Test
    void login_withDemoExpired_showsDemoExpiredMessage() throws Exception {
        mockMvc.perform(get("/login").param("demo-expired", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your demo has expired")));
    }
}
