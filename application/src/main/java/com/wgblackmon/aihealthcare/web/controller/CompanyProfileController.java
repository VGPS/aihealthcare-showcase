package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanyEvent;
import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller serving company intelligence profile pages.
 *
 * <p>{@code GET /companies} renders the company index (all profiles sorted
 * by article count). {@code GET /companies/{slug}} renders the detail page
 * with event timeline.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
@Controller
public class CompanyProfileController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.of("America/New_York"));

    private final CompanyProfilePort companyProfilePort;
    private final CompanyEventPort companyEventPort;

    public CompanyProfileController(CompanyProfilePort companyProfilePort,
                                    CompanyEventPort companyEventPort) {
        log.debug("CompanyProfileController() | companyProfilePort={}, companyEventPort={}",
                  companyProfilePort, companyEventPort);
        this.companyProfilePort = companyProfilePort;
        this.companyEventPort = companyEventPort;
    }

    /**
     * Renders the company index page — all company profiles ordered by article count.
     *
     * @param model Thymeleaf model
     * @return the "company-index" view name
     */
    @GetMapping("/companies")
    public String index(Model model) {
        log.debug("index()");

        List<CompanyProfile> profiles = companyProfilePort.findAll();

        // Format timestamps server-side
        Map<String, String> discoveredDates = new HashMap<>();
        Map<String, String> updatedDates = new HashMap<>();
        for (CompanyProfile profile : profiles) {
            discoveredDates.put(profile.slug(), DISPLAY_FMT.format(profile.firstDiscoveredAt()));
            updatedDates.put(profile.slug(), DISPLAY_FMT.format(profile.lastUpdatedAt()));
        }

        model.addAttribute("profiles", profiles);
        model.addAttribute("discoveredDates", discoveredDates);
        model.addAttribute("updatedDates", updatedDates);
        model.addAttribute("profileCount", profiles.size());

        log.debug("index() | return=company-index, profileCount={}", profiles.size());
        return "company-index";
    }

    /**
     * Renders the company detail page with event timeline.
     *
     * @param slug  the company slug
     * @param model Thymeleaf model
     * @return the "company-detail" view name, or redirect to index if not found
     */
    @GetMapping("/companies/{slug}")
    public String detail(@PathVariable String slug, Model model) {
        log.debug("detail() | slug={}", slug);

        Optional<CompanyProfile> profileOpt = companyProfilePort.findBySlug(slug);
        if (profileOpt.isEmpty()) {
            log.debug("detail() | return=redirect:/companies (not found)");
            return "redirect:/companies";
        }

        CompanyProfile profile = profileOpt.get();
        List<CompanyEvent> events = companyEventPort.findByCompanySlug(slug);

        // Format event dates server-side
        Map<String, String> eventDates = new HashMap<>();
        for (CompanyEvent event : events) {
            if (event.occurredAt() != null) {
                eventDates.put(event.eventId(), DISPLAY_FMT.format(event.occurredAt()));
            }
        }

        model.addAttribute("profile", profile);
        model.addAttribute("events", events);
        model.addAttribute("eventDates", eventDates);
        model.addAttribute("discoveredAt", DISPLAY_FMT.format(profile.firstDiscoveredAt()));
        model.addAttribute("updatedAt", DISPLAY_FMT.format(profile.lastUpdatedAt()));

        log.debug("detail() | return=company-detail, eventCount={}", events.size());
        return "company-detail";
    }
}
