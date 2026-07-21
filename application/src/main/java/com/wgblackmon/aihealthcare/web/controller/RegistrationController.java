package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.exception.DuplicateUserException;
import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.port.inbound.RegisterUserUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Thymeleaf controller for the self-registration flow at {@code /register}.
 *
 * <p>Renders a registration form (GET) and processes new DEMO user creation
 * (POST). On successful registration the user is automatically logged in
 * and redirected to the dashboard.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
@Slf4j
@Controller
public class RegistrationController {

    private final RegisterUserUseCase registerUserUseCase;

    public RegistrationController(RegisterUserUseCase registerUserUseCase) {
        log.debug("RegistrationController() | registerUserUseCase={}", registerUserUseCase.getClass().getSimpleName());
        this.registerUserUseCase = registerUserUseCase;
    }

    /**
     * Renders the registration form.
     *
     * @return Thymeleaf view name "register".
     */
    @GetMapping("/register")
    public String showForm() {
        log.debug("showForm() | (no args)");
        log.debug("showForm() | return=register");
        return "register";
    }

    /**
     * Processes registration form submission.
     *
     * <p>Validates password confirmation, creates the DEMO user, auto-logs in,
     * and redirects to the dashboard. On failure, re-renders the form with an
     * error message.
     *
     * @param email           the user's email address.
     * @param displayName     the user's display name.
     * @param password        the chosen password.
     * @param confirmPassword password confirmation (must match).
     * @param model           Thymeleaf model for error feedback.
     * @param request         HTTP request for session-based auto-login.
     * @return redirect to dashboard on success, or "register" view on error.
     */
    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam String displayName,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           Model model,
                           HttpServletRequest request) {
        log.debug("register() | email={}, displayName={}", email, displayName);

        if (email == null || email.isBlank() || displayName == null || displayName.isBlank()
                || password == null || password.isBlank()) {
            model.addAttribute("error", "All fields are required.");
            model.addAttribute("email", email);
            model.addAttribute("displayName", displayName);
            log.debug("register() | return=register (blank fields)");
            return "register";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("email", email);
            model.addAttribute("displayName", displayName);
            log.debug("register() | return=register (password mismatch)");
            return "register";
        }

        try {
            AppUser user = registerUserUseCase.register(email, displayName, password);

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    user.email(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
            SecurityContextHolder.getContext().setAuthentication(auth);

            HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());

            log.debug("register() | return=redirect:/dashboard (success)");
            return "redirect:/dashboard";
        } catch (DuplicateUserException e) {
            model.addAttribute("error", "An account with this email already exists.");
            model.addAttribute("email", email);
            model.addAttribute("displayName", displayName);
            log.debug("register() | return=register (duplicate)");
            return "register";
        }
    }
}
