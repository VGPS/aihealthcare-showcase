package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf controller that renders the 403 Access Denied page.
 *
 * <p>Spring Security redirects here when an authenticated user attempts to
 * access a page that requires a role they do not hold (e.g. a USER trying
 * to reach an ADMIN-only page like newsletter editing).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-30
 * @updated 2026-05-30
 */
@Slf4j
@Controller
public class AccessDeniedController {

    /**
     * Renders the access-denied page.
     *
     * @return Thymeleaf view name "access-denied".
     */
    @GetMapping("/access-denied")
    public String accessDenied() {
        log.debug("accessDenied() | (no args)");
        log.debug("accessDenied() | return=access-denied");
        return "access-denied";
    }
}
