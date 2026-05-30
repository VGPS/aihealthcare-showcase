package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link AccessDeniedController}.
 *
 * <p>Verifies the 403 access-denied page renders correctly for
 * authenticated users who lack the required role.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-30
 * @updated 2026-05-30
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(AccessDeniedController.class)
class AccessDeniedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void accessDenied_rendersPage() throws Exception {
        mockMvc.perform(get("/access-denied"))
                .andExpect(status().isOk())
                .andExpect(view().name("access-denied"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void accessDenied_asAdmin_rendersPage() throws Exception {
        mockMvc.perform(get("/access-denied"))
                .andExpect(status().isOk())
                .andExpect(view().name("access-denied"));
    }
}
