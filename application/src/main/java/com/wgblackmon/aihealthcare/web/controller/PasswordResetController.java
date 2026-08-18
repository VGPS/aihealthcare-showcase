package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.exception.InvalidResetTokenException;
import com.wgblackmon.aihealthcare.domain.port.inbound.PasswordResetUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Thymeleaf controller for the self-service "forgot password" flow.
 *
 * <p>{@code GET/POST /forgot-password} — request a reset link by email.
 * The response is identical whether or not the email is registered, so this
 * page cannot be used to enumerate accounts.
 *
 * <p>{@code GET/POST /reset-password} — consume a token and set a new
 * password. Both endpoints are publicly accessible (no authentication
 * required) since a user who forgot their password cannot log in.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-08-17
 */
@Slf4j
@Controller
public class PasswordResetController {

    private final PasswordResetUseCase passwordResetUseCase;

    public PasswordResetController(PasswordResetUseCase passwordResetUseCase) {
        log.debug("PasswordResetController() | passwordResetUseCase={}", passwordResetUseCase.getClass().getSimpleName());
        this.passwordResetUseCase = passwordResetUseCase;
    }

    /**
     * Renders the "request a reset link" form.
     *
     * @return Thymeleaf view name "forgot-password".
     */
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        log.debug("showForgotPasswordForm() | (no args)");
        log.debug("showForgotPasswordForm() | return=forgot-password");
        return "forgot-password";
    }

    /**
     * Processes a reset-link request. Always shows the same generic
     * confirmation, regardless of whether the email is registered.
     *
     * @param email the email address to send a reset link to, if registered.
     * @param model Thymeleaf model.
     * @return Thymeleaf view name "forgot-password".
     */
    @PostMapping("/forgot-password")
    public String requestReset(@RequestParam String email, Model model) {
        log.debug("requestReset() | email={}", email);

        passwordResetUseCase.requestReset(email);
        model.addAttribute("submitted", true);

        log.debug("requestReset() | return=forgot-password");
        return "forgot-password";
    }

    /**
     * Renders the "set a new password" form, or an error if the token is
     * missing, expired, or already used.
     *
     * @param token the reset token from the emailed link.
     * @param model Thymeleaf model.
     * @return Thymeleaf view name "reset-password".
     */
    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam(required = false) String token, Model model) {
        log.debug("showResetPasswordForm() | token=[REDACTED]");

        boolean tokenValid = token != null && !token.isBlank() && passwordResetUseCase.validateToken(token);
        model.addAttribute("tokenValid", tokenValid);
        model.addAttribute("token", token);

        log.debug("showResetPasswordForm() | return=reset-password");
        return "reset-password";
    }

    /**
     * Processes a new-password submission for the given token.
     *
     * @param token           the reset token from the emailed link.
     * @param newPassword     the chosen new password.
     * @param confirmPassword password confirmation (must match).
     * @param model           Thymeleaf model for error feedback.
     * @return redirect to login on success, or "reset-password" view on error.
     */
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                Model model) {
        log.debug("resetPassword() | token=[REDACTED]");

        if (newPassword == null || newPassword.isBlank()) {
            model.addAttribute("error", "Please enter a new password.");
            model.addAttribute("tokenValid", true);
            model.addAttribute("token", token);
            log.debug("resetPassword() | return=reset-password (blank password)");
            return "reset-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("tokenValid", true);
            model.addAttribute("token", token);
            log.debug("resetPassword() | return=reset-password (password mismatch)");
            return "reset-password";
        }

        try {
            passwordResetUseCase.resetPassword(token, newPassword);
            log.debug("resetPassword() | return=redirect:/login?reset=success");
            return "redirect:/login?reset=success";
        } catch (InvalidResetTokenException e) {
            model.addAttribute("tokenValid", false);
            log.debug("resetPassword() | return=reset-password (invalid token)");
            return "reset-password";
        }
    }
}
