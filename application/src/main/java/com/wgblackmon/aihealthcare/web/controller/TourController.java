package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf controller for the public product walkthrough page at {@code GET /tour}.
 *
 * <p>Renders an embedded video tour of the platform's dashboards, captured as
 * a SUBSCRIBER-tier account so it shows what a paying customer sees (no admin
 * tooling). Publicly accessible (no auth required) for use in marketing posts
 * and outreach links.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-25
 * @updated 2026-09-25
 */
@Slf4j
@Controller
public class TourController {

    @GetMapping("/tour")
    public String tour(Model model) {
        log.debug("tour() | rendering product tour page");
        model.addAttribute("pageDescription",
                "A guided video tour of AI Healthcare Intelligence — market digests, deal signals, regulatory tracking, sentiment analysis, and more.");
        log.debug("tour() | return=tour");
        return "tour";
    }
}
