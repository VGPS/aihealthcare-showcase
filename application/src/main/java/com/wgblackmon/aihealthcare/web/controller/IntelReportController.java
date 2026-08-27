package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.IntelReport;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateIntelReportUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller for competitive intelligence report generation and browsing.
 *
 * <p>Serves the report list page with a generation form, and individual report
 * detail views. Tier-gated to SUBSCRIBER/DEMO/ADMIN users.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@Controller
public class IntelReportController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
                    .withZone(ZoneId.of("America/New_York"));

    private final GenerateIntelReportUseCase intelReportUseCase;
    private final SubscriberPort subscriberPort;

    public IntelReportController(GenerateIntelReportUseCase intelReportUseCase,
                                 SubscriberPort subscriberPort) {
        log.debug("IntelReportController() | intelReportUseCase={}, subscriberPort={}",
                  intelReportUseCase.getClass().getSimpleName(),
                  subscriberPort.getClass().getSimpleName());
        this.intelReportUseCase = intelReportUseCase;
        this.subscriberPort = subscriberPort;
        log.debug("IntelReportController() | return=void");
    }

    /**
     * Renders the intel report list page with a query form.
     */
    @GetMapping("/research/intel")
    public String listReports(Principal principal, Model model) {
        log.debug("listReports() | principal={}", principal != null ? principal.getName() : "anonymous");

        SubscriptionTier tier = resolveTier(principal);
        boolean fullAccess = tier == SubscriptionTier.SUBSCRIBER
                || tier == SubscriptionTier.DEMO
                || tier == SubscriptionTier.ENTERPRISE
                || isAdmin(principal);

        if (fullAccess) {
            List<IntelReport> reports = intelReportUseCase.findAll();
            model.addAttribute("reports", reports);

            Map<String, String> reportDates = new HashMap<>();
            for (IntelReport report : reports) {
                reportDates.put(report.reportId(), DISPLAY_FMT.format(report.generatedAt()));
            }
            model.addAttribute("reportDates", reportDates);
        }

        model.addAttribute("fullAccess", fullAccess);
        model.addAttribute("tier", tier.name());

        log.debug("listReports() | return=intel-reports, fullAccess={}", fullAccess);
        return "intel-reports";
    }

    /**
     * Renders a single intel report detail page.
     */
    @GetMapping("/research/intel/{reportId}")
    public String viewReport(@PathVariable String reportId,
                             Principal principal,
                             Model model) {
        log.debug("viewReport() | reportId={}, principal={}", reportId,
                  principal != null ? principal.getName() : "anonymous");

        SubscriptionTier tier = resolveTier(principal);
        boolean fullAccess = tier == SubscriptionTier.SUBSCRIBER
                || tier == SubscriptionTier.DEMO
                || tier == SubscriptionTier.ENTERPRISE
                || isAdmin(principal);

        if (!fullAccess) {
            model.addAttribute("fullAccess", false);
            model.addAttribute("tier", tier.name());
            log.debug("viewReport() | return=intel-reports (no access)");
            return "intel-reports";
        }

        Optional<IntelReport> reportOpt = intelReportUseCase.findById(reportId);
        if (reportOpt.isEmpty()) {
            model.addAttribute("errorMessage", "Report not found: " + reportId);
            model.addAttribute("fullAccess", true);
            List<IntelReport> reports = intelReportUseCase.findAll();
            model.addAttribute("reports", reports);
            Map<String, String> reportDates = new HashMap<>();
            for (IntelReport r : reports) {
                reportDates.put(r.reportId(), DISPLAY_FMT.format(r.generatedAt()));
            }
            model.addAttribute("reportDates", reportDates);
            log.debug("viewReport() | return=intel-reports (not found)");
            return "intel-reports";
        }

        IntelReport report = reportOpt.get();
        model.addAttribute("report", report);
        model.addAttribute("generatedDate", DISPLAY_FMT.format(report.generatedAt()));

        Map<Integer, String> sourceDates = new HashMap<>();
        for (SourceCitation s : report.sources()) {
            if (s.retrievedAt() != null) {
                sourceDates.put(s.citationNumber(), DISPLAY_FMT.format(s.retrievedAt()));
            }
        }
        model.addAttribute("sourceDates", sourceDates);

        log.debug("viewReport() | return=intel-report-detail, sources={}", report.sources().size());
        return "intel-report-detail";
    }

    /**
     * Generates a new intel report and redirects to its detail page.
     */
    @PostMapping("/research/intel/generate")
    public String generateReport(@RequestParam String query,
                                 Principal principal,
                                 Model model) {
        log.debug("generateReport() | query={}, principal={}", query,
                  principal != null ? principal.getName() : "anonymous");

        SubscriptionTier tier = resolveTier(principal);
        boolean fullAccess = tier == SubscriptionTier.SUBSCRIBER
                || tier == SubscriptionTier.DEMO
                || tier == SubscriptionTier.ENTERPRISE
                || isAdmin(principal);

        if (!fullAccess) {
            model.addAttribute("fullAccess", false);
            model.addAttribute("tier", tier.name());
            log.debug("generateReport() | return=intel-reports (no access)");
            return "intel-reports";
        }

        String userEmail = principal != null ? principal.getName() : "anonymous";

        try {
            IntelReport report = intelReportUseCase.generate(query, userEmail);
            log.info("generateReport() | report generated: reportId={}", report.reportId());
            log.debug("generateReport() | return=redirect to {}", report.reportId());
            return "redirect:/research/intel/" + report.reportId();
        } catch (Exception e) {
            log.error("generateReport() | failed: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Report generation failed: " + e.getMessage());
            model.addAttribute("fullAccess", true);
            model.addAttribute("query", query);
            List<IntelReport> reports = intelReportUseCase.findAll();
            model.addAttribute("reports", reports);

            Map<String, String> reportDates = new HashMap<>();
            for (IntelReport r : reports) {
                reportDates.put(r.reportId(), DISPLAY_FMT.format(r.generatedAt()));
            }
            model.addAttribute("reportDates", reportDates);

            log.debug("generateReport() | return=intel-reports (error)");
            return "intel-reports";
        }
    }

    private SubscriptionTier resolveTier(Principal principal) {
        log.debug("resolveTier() | principal={}", principal != null ? principal.getName() : "null");
        if (principal == null) {
            log.debug("resolveTier() | return={}", SubscriptionTier.FREE);
            return SubscriptionTier.FREE;
        }
        if (isAdmin(principal)) {
            log.debug("resolveTier() | ADMIN role detected, return={}", SubscriptionTier.SUBSCRIBER);
            return SubscriptionTier.SUBSCRIBER;
        }
        Optional<Subscriber> subscriber = subscriberPort.findByEmail(principal.getName());
        SubscriptionTier result = subscriber.map(Subscriber::tier).orElse(SubscriptionTier.FREE);
        log.debug("resolveTier() | return={}", result);
        return result;
    }

    private boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication auth) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }
}
