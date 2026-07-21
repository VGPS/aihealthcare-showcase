package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.exception.DuplicateUserException;
import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.RegisterUserUseCase;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
 * MockMvc tests for {@link RegistrationController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
@Import(SecurityConfig.class)
@WebMvcTest(RegistrationController.class)
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterUserUseCase registerUserUseCase;

    @MockitoBean
    private com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort apiKeyPort;

    @Test
    void getRegister_rendersForm() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("Start Your Free Trial")));
    }

    @Test
    void postRegister_success_redirectsToDashboard() throws Exception {
        AppUser demoUser = new AppUser("new@example.com", "hash", "New User", "USER", true,
                SubscriptionTier.DEMO, Instant.now().plus(7, ChronoUnit.DAYS));
        when(registerUserUseCase.register("new@example.com", "New User", "password123"))
                .thenReturn(demoUser);

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("email", "new@example.com")
                        .param("displayName", "New User")
                        .param("password", "password123")
                        .param("confirmPassword", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void postRegister_passwordMismatch_showsError() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("email", "new@example.com")
                        .param("displayName", "New User")
                        .param("password", "password123")
                        .param("confirmPassword", "different"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("Passwords do not match")));

        verify(registerUserUseCase, never()).register(anyString(), anyString(), anyString());
    }

    @Test
    void postRegister_duplicateEmail_showsError() throws Exception {
        when(registerUserUseCase.register("existing@example.com", "User", "password123"))
                .thenThrow(new DuplicateUserException("existing@example.com"));

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("email", "existing@example.com")
                        .param("displayName", "User")
                        .param("password", "password123")
                        .param("confirmPassword", "password123"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("already exists")));
    }

    @Test
    void postRegister_blankFields_showsError() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("email", "")
                        .param("displayName", "")
                        .param("password", "")
                        .param("confirmPassword", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("All fields are required")));

        verify(registerUserUseCase, never()).register(anyString(), anyString(), anyString());
    }
}
