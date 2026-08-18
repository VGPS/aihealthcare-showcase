package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.exception.InvalidResetTokenException;
import com.wgblackmon.aihealthcare.domain.port.inbound.PasswordResetUseCase;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
 * MockMvc tests for {@link PasswordResetController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-08-17
 */
@Import(SecurityConfig.class)
@WebMvcTest(PasswordResetController.class)
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetUseCase passwordResetUseCase;

    @MockitoBean
    private com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort apiKeyPort;

    @Test
    void getForgotPassword_rendersForm() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(content().string(containsString("Forgot Password")));
    }

    @Test
    void postForgotPassword_knownEmail_showsGenericConfirmation() throws Exception {
        mockMvc.perform(post("/forgot-password").with(csrf()).param("email", "user@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(content().string(containsString("Check Your Email")));

        verify(passwordResetUseCase).requestReset("user@example.com");
    }

    @Test
    void postForgotPassword_unknownEmail_showsSameGenericConfirmation() throws Exception {
        mockMvc.perform(post("/forgot-password").with(csrf()).param("email", "nobody@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(content().string(containsString("Check Your Email")));
    }

    @Test
    void getResetPassword_validToken_rendersForm() throws Exception {
        when(passwordResetUseCase.validateToken("tok-1")).thenReturn(true);

        mockMvc.perform(get("/reset-password").param("token", "tok-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(content().string(containsString("Set a New Password")));
    }

    @Test
    void getResetPassword_invalidToken_rendersErrorState() throws Exception {
        when(passwordResetUseCase.validateToken("bad-tok")).thenReturn(false);

        mockMvc.perform(get("/reset-password").param("token", "bad-tok"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(content().string(containsString("Link Invalid or Expired")));
    }

    @Test
    void getResetPassword_missingToken_rendersErrorState() throws Exception {
        mockMvc.perform(get("/reset-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(content().string(containsString("Link Invalid or Expired")));

        verify(passwordResetUseCase, never()).validateToken(anyString());
    }

    @Test
    void postResetPassword_success_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "tok-1")
                        .param("newPassword", "newPassword123")
                        .param("confirmPassword", "newPassword123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reset=success"));

        verify(passwordResetUseCase).resetPassword("tok-1", "newPassword123");
    }

    @Test
    void postResetPassword_passwordMismatch_showsError() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "tok-1")
                        .param("newPassword", "newPassword123")
                        .param("confirmPassword", "different"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(content().string(containsString("Passwords do not match")));

        verify(passwordResetUseCase, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void postResetPassword_invalidToken_showsErrorState() throws Exception {
        org.mockito.Mockito.doThrow(new InvalidResetTokenException())
                .when(passwordResetUseCase).resetPassword("bad-tok", "newPassword123");

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "bad-tok")
                        .param("newPassword", "newPassword123")
                        .param("confirmPassword", "newPassword123"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(content().string(containsString("Link Invalid or Expired")));
    }
}
