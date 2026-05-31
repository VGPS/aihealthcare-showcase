package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Admin panel controller for user management and system status overview.
 *
 * <p>Serves {@code GET /admin} — restricted to users with the {@code ADMIN}
 * role via {@link com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig}.
 * Displays all registered users (role, enabled status) and high-level system
 * statistics (article counts, newsletter runs, subscribers).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-31
 * @updated 2026-05-31
 */
@Slf4j
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AppUserPort appUserPort;
    private final GetAnalyticsUseCase analyticsUseCase;
    private final SubscriberPort subscriberPort;

    public AdminController(AppUserPort appUserPort,
                           GetAnalyticsUseCase analyticsUseCase,
                           SubscriberPort subscriberPort) {
        log.debug("AdminController() | appUserPort={}, analyticsUseCase={}, subscriberPort={}",
                appUserPort.getClass().getSimpleName(),
                analyticsUseCase.getClass().getSimpleName(),
                subscriberPort.getClass().getSimpleName());
        this.appUserPort = appUserPort;
        this.analyticsUseCase = analyticsUseCase;
        this.subscriberPort = subscriberPort;
    }

    /**
     * Renders the admin panel page with user list and system status.
     *
     * @param model The Thymeleaf model.
     * @return View name "admin".
     */
    @GetMapping
    public String adminPanel(Model model) {
        log.debug("adminPanel() | (no args)");

        List<AppUser> users = appUserPort.findAll();
        model.addAttribute("users", users);

        long adminCount = 0;
        long userCount = 0;
        long disabledCount = 0;
        for (AppUser user : users) {
            if ("ADMIN".equals(user.role())) {
                adminCount++;
            } else {
                userCount++;
            }
            if (!user.enabled()) {
                disabledCount++;
            }
        }
        model.addAttribute("adminCount", adminCount);
        model.addAttribute("userCount", userCount);
        model.addAttribute("disabledCount", disabledCount);

        model.addAttribute("ingestion", analyticsUseCase.getIngestionAnalytics());
        model.addAttribute("runs", analyticsUseCase.getRunAnalytics());

        int subscriberCount = subscriberPort.findAll().size();
        model.addAttribute("subscriberCount", subscriberCount);

        log.debug("adminPanel() | return=admin (users={}, subscribers={})", users.size(), subscriberCount);
        return "admin";
    }
}
