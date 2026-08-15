package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf controller for the public press/share page at {@code GET /press}.
 *
 * <p>Publicly accessible, no auth required. Used as a shareable link in
 * social media, developer communities, and email outreach.
 *
 * @author  Bill Blackmon
 * @since   2026-08-15
 * @updated 2026-08-15
 */
@Slf4j
@Controller
public class PressController {

    @GetMapping("/press")
    public String press() {
        log.debug("press() | return=press");
        return "press";
    }
}
