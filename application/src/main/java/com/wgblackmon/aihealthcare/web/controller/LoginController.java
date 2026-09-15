package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoIdentityProviderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Thymeleaf controller that renders the login page at {@code GET /login}.
 *
 * <p>Spring Security is configured to redirect unauthenticated users here.
 * The login form POSTs to {@code /login} (handled by Spring Security's
 * built-in {@code UsernamePasswordAuthenticationFilter}), so this controller
 * only serves the GET request for the page itself.
 *
 * <p>When active SSO Identity Providers are configured, the login page
 * displays "Sign in with {label}" buttons below the password form.
 *
 * <p>Query parameters {@code ?error} and {@code ?logout} are handled in the
 * Thymeleaf template to display appropriate feedback messages.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-28
 * @updated 2026-09-15 — SSO-1: add SSO provider buttons to login page
 */
@Slf4j
@Controller
public class LoginController {

    private final SsoIdentityProviderPort providerPort;

    public LoginController(SsoIdentityProviderPort providerPort) {
        log.debug("LoginController() | providerPort={}", providerPort.getClass().getSimpleName());
        this.providerPort = providerPort;
    }

    /**
     * Renders the login page with optional SSO provider buttons.
     *
     * @param model the Thymeleaf model.
     * @return Thymeleaf view name "login".
     */
    @GetMapping("/login")
    public String login(Model model) {
        log.debug("login() | (no args)");

        List<SsoIdentityProvider> ssoProviders = providerPort.findAllActive();
        model.addAttribute("ssoProviders", ssoProviders);

        log.debug("login() | return=login, ssoProviders={}", ssoProviders.size());
        return "login";
    }
}
