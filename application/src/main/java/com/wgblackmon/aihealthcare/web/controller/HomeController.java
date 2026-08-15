package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf controller for the public homepage at {@code GET /}.
 *
 * <p>Authenticated users are redirected to the dashboard. Unauthenticated
 * visitors see the platform marketing homepage.
 *
 * @author  Bill Blackmon
 * @since   2026-08-15
 * @updated 2026-08-15
 */
@Slf4j
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication auth) {
        log.debug("home() | auth={}", auth != null ? auth.getName() : "anonymous");
        if (auth != null && auth.isAuthenticated()) {
            log.debug("home() | return=redirect:/dashboard");
            return "redirect:/dashboard";
        }
        log.debug("home() | return=home");
        return "home";
    }
}
