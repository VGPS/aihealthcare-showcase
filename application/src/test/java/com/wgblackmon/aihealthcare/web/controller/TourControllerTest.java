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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link TourController}.
 *
 * <p>Verifies the public product tour page is accessible without
 * authentication and returns the correct view name.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-25
 * @updated 2026-09-25
 */
@Import(SecurityConfig.class)
@WebMvcTest(TourController.class)
class TourControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    void tour_unauthenticated_returns200() throws Exception {
        mockMvc.perform(get("/tour"))
                .andExpect(status().isOk())
                .andExpect(view().name("tour"));
    }

    @Test
    @WithMockUser
    void tour_authenticatedUser_returns200() throws Exception {
        mockMvc.perform(get("/tour"))
                .andExpect(status().isOk())
                .andExpect(view().name("tour"));
    }
}
