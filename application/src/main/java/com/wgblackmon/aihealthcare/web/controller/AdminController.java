package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import com.wgblackmon.aihealthcare.domain.model.CountByLabel;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Admin panel controller for user management and system status overview.
 *
 * <p>Serves {@code GET /admin} — restricted to users with the {@code ADMIN}
 * role via {@link com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig}.
 * Displays all registered users (role, enabled status) and high-level system
 * statistics (article counts, newsletter runs, subscribers).
 *
 * <p>Also provides {@code POST} actions for toggling user enabled/disabled
 * status and changing user roles.  An admin cannot modify their own account
 * to prevent accidental lock-out.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-31
 * @updated 2026-08-07
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
     * @param model     The Thymeleaf model.
     * @param principal The currently authenticated user.
     * @return View name "admin".
     */
    @GetMapping
    public String adminPanel(Model model, Principal principal) {
        log.debug("adminPanel() | principal={}", principal.getName());

        List<AppUser> users = appUserPort.findAll();
        model.addAttribute("users", users);
        model.addAttribute("currentUserEmail", principal.getName());

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

        IngestionAnalytics ingestion = analyticsUseCase.getIngestionAnalytics();
        model.addAttribute("ingestion", ingestion);
        model.addAttribute("runs", analyticsUseCase.getRunAnalytics());

        int subscriberCount = subscriberPort.findAll().size();
        model.addAttribute("subscriberCount", subscriberCount);

        // Analytics chart data: articles per day (last 30 days)
        List<CountByLabel> dailyCounts = analyticsUseCase.getDailyArticleCounts(30);
        List<String> chartLabels = new ArrayList<>();
        List<Long> chartData = new ArrayList<>();
        for (CountByLabel entry : dailyCounts) {
            chartLabels.add(entry.label());
            chartData.add(entry.count());
        }
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartData", chartData);

        // Analytics chart data: topic distribution (top 10)
        List<CountByLabel> topicCounts = analyticsUseCase.getTopicDistribution(10);
        List<String> topicLabels = new ArrayList<>();
        List<Long> topicData = new ArrayList<>();
        for (CountByLabel entry : topicCounts) {
            String topicName = entry.label();
            if (topicName.length() > 25) {
                topicName = topicName.substring(0, 22) + "...";
            }
            topicLabels.add(topicName);
            topicData.add(entry.count());
        }
        model.addAttribute("topicChartLabels", topicLabels);
        model.addAttribute("topicChartData", topicData);
        model.addAttribute("last30DaysCount", ingestion.last30DaysCount());
        model.addAttribute("totalArticles", ingestion.totalArticles());

        log.debug("adminPanel() | return=admin (users={}, subscribers={})", users.size(), subscriberCount);
        return "admin";
    }

    /**
     * Toggles a user's enabled/disabled status.
     *
     * <p>An admin cannot toggle their own account to prevent accidental lock-out.
     *
     * @param email              The target user's email address.
     * @param principal          The currently authenticated admin.
     * @param redirectAttributes Flash attributes for success/error messages.
     * @return Redirect to the admin panel.
     */
    @PostMapping("/users/{email}/toggle-enabled")
    public String toggleEnabled(@PathVariable String email,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        log.debug("toggleEnabled() | email={}, principal={}", email, principal.getName());

        if (email.equals(principal.getName())) {
            log.warn("toggleEnabled() | admin attempted to toggle own account: {}", LogSanitizer.maskEmail(email));
            redirectAttributes.addFlashAttribute("errorMessage", "You cannot enable/disable your own account.");
            log.debug("toggleEnabled() | return=redirect:/admin (self-action blocked)");
            return "redirect:/admin";
        }

        Optional<AppUser> existing = appUserPort.findByEmail(email);
        if (existing.isEmpty()) {
            log.warn("toggleEnabled() | user not found: {}", LogSanitizer.maskEmail(email));
            redirectAttributes.addFlashAttribute("errorMessage", "User not found: " + email);
            log.debug("toggleEnabled() | return=redirect:/admin (not found)");
            return "redirect:/admin";
        }

        AppUser user = existing.get();
        AppUser updated = new AppUser(user.email(), user.passwordHash(), user.displayName(),
                user.role(), !user.enabled(), user.tier(), user.demoExpiresAt());
        appUserPort.save(updated);

        String action = updated.enabled() ? "enabled" : "disabled";
        redirectAttributes.addFlashAttribute("successMessage",
                "User " + email + " has been " + action + ".");

        log.debug("toggleEnabled() | return=redirect:/admin ({})", action);
        return "redirect:/admin";
    }

    /**
     * Changes a user's role.
     *
     * <p>An admin cannot change their own role to prevent accidental lock-out.
     *
     * @param email              The target user's email address.
     * @param role               The new role to assign (e.g. "USER" or "ADMIN").
     * @param principal          The currently authenticated admin.
     * @param redirectAttributes Flash attributes for success/error messages.
     * @return Redirect to the admin panel.
     */
    @PostMapping("/users/{email}/change-role")
    public String changeRole(@PathVariable String email,
                             @RequestParam String role,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        log.debug("changeRole() | email={}, role={}, principal={}", email, role, principal.getName());

        if (email.equals(principal.getName())) {
            log.warn("changeRole() | admin attempted to change own role: {}", LogSanitizer.maskEmail(email));
            redirectAttributes.addFlashAttribute("errorMessage", "You cannot change your own role.");
            log.debug("changeRole() | return=redirect:/admin (self-action blocked)");
            return "redirect:/admin";
        }

        Optional<AppUser> existing = appUserPort.findByEmail(email);
        if (existing.isEmpty()) {
            log.warn("changeRole() | user not found: {}", LogSanitizer.maskEmail(email));
            redirectAttributes.addFlashAttribute("errorMessage", "User not found: " + email);
            log.debug("changeRole() | return=redirect:/admin (not found)");
            return "redirect:/admin";
        }

        AppUser user = existing.get();
        AppUser updated = new AppUser(user.email(), user.passwordHash(), user.displayName(),
                role, user.enabled(), user.tier(), user.demoExpiresAt());
        appUserPort.save(updated);

        redirectAttributes.addFlashAttribute("successMessage",
                "User " + email + " role changed to " + role + ".");

        log.debug("changeRole() | return=redirect:/admin (role={})", role);
        return "redirect:/admin";
    }
}
