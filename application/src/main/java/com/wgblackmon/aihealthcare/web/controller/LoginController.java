package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf controller that renders the login page at {@code GET /login}.
 *
 * <p>Spring Security is configured to redirect unauthenticated users here.
 * The login form POSTs to {@code /login} (handled by Spring Security's
 * built-in {@code UsernamePasswordAuthenticationFilter}), so this controller
 * only serves the GET request for the page itself.
 *
 * <p>Query parameters {@code ?error} and {@code ?logout} are handled in the
 * Thymeleaf template to display appropriate feedback messages.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-28
 * @updated 2026-05-28
 */
@Slf4j
@Controller
public class LoginController {

    /**
     * Renders the login page.
     *
     * @return Thymeleaf view name "login".
     */
    @GetMapping("/login")
    public String login() {
        log.debug("login() | (no args)");
        log.debug("login() | return=login");
        return "login";
    }
}
