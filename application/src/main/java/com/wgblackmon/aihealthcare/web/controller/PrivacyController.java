package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf controller for the public Privacy Policy page at {@code GET /privacy}.
 *
 * <p>Renders a static privacy/data-handling policy page. Publicly accessible
 * (no auth required) — linked from the site footer on every page, and required
 * reference material for the SES production-access review process.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
@Slf4j
@Controller
public class PrivacyController {

    @GetMapping("/privacy")
    public String privacy() {
        log.debug("privacy() | rendering privacy policy page");
        log.debug("privacy() | return=privacy");
        return "privacy";
    }
}
