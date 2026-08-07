package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf controller for the public About page at {@code GET /about}.
 *
 * <p>Renders a portfolio/showcase page describing the platform's architecture,
 * capabilities, and the engineering behind it. Publicly accessible (no auth
 * required) to serve as a professional showcase for potential employers and
 * subscribers.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-07
 * @updated 2026-08-07
 */
@Slf4j
@Controller
public class AboutController {

    @GetMapping("/about")
    public String about() {
        log.debug("about() | rendering about page");
        log.debug("about() | return=about");
        return "about";
    }
}
