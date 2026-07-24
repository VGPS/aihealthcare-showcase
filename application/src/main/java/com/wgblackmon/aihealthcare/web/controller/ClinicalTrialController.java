package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialPhase;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorClinicalTrialsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Thymeleaf controller that renders the clinical trials dashboard page.
 *
 * <p>Serves {@code GET /dashboard/clinical-trials} by loading recent
 * {@link ClinicalTrial} records and populating the Thymeleaf model
 * with trials grouped and filtered by status and phase.
 *
 * <p>Tier gating: SUBSCRIBER and DEMO users see all trials;
 * FREE users see only the 5 most recent.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
@Slf4j
@Controller
public class ClinicalTrialController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.of("America/New_York"));

    private static final int FREE_TRIAL_LIMIT = 5;
    private static final int FULL_TRIAL_LIMIT = 50;

    private final MonitorClinicalTrialsUseCase clinicalTrialsUseCase;
    private final SubscriberPort subscriberPort;

    public ClinicalTrialController(MonitorClinicalTrialsUseCase clinicalTrialsUseCase,
                                    SubscriberPort subscriberPort) {
        log.debug("ClinicalTrialController() | clinicalTrialsUseCase={}, subscriberPort={}",
                  clinicalTrialsUseCase, subscriberPort);
        this.clinicalTrialsUseCase = clinicalTrialsUseCase;
        this.subscriberPort = subscriberPort;
    }

    /**
     * Renders the clinical trials page.
     *
     * @param filter    optional filter: "recruiting", "completed", or null for all
     * @param principal the authenticated user, or null for anonymous
     * @param model     Thymeleaf model
     * @return the "clinical-trials" view name
     */
    @GetMapping("/dashboard/clinical-trials")
    public String clinicalTrials(@RequestParam(required = false) String filter,
                                  Principal principal,
                                  Model model) {
        log.debug("clinicalTrials() | filter={}, principal={}", filter,
                  principal != null ? principal.getName() : "anonymous");

        SubscriptionTier tier = resolveTier(principal);
        boolean fullAccess = tier == SubscriptionTier.SUBSCRIBER
                || tier == SubscriptionTier.DEMO
                || isAdmin(principal);
        int limit = fullAccess ? FULL_TRIAL_LIMIT : FREE_TRIAL_LIMIT;

        List<ClinicalTrial> trials;
        if ("recruiting".equalsIgnoreCase(filter)) {
            trials = clinicalTrialsUseCase.getTrialsByStatus(ClinicalTrialStatus.RECRUITING, limit);
        } else if ("completed".equalsIgnoreCase(filter)) {
            trials = clinicalTrialsUseCase.getTrialsByStatus(ClinicalTrialStatus.COMPLETED, limit);
        } else {
            trials = clinicalTrialsUseCase.getRecentTrials(limit);
        }

        // Format dates server-side
        Map<String, String> startDates = new HashMap<>();
        Map<String, String> discoveredDates = new HashMap<>();
        for (ClinicalTrial trial : trials) {
            if (trial.startDate() != null) {
                startDates.put(trial.trialId(), DISPLAY_FMT.format(trial.startDate()));
            }
            discoveredDates.put(trial.trialId(), DISPLAY_FMT.format(trial.discoveredAt()));
        }

        // Status counts for summary badges
        int recruitingCount = 0;
        int completedCount = 0;
        int phase2Count = 0;
        int phase3Count = 0;
        for (ClinicalTrial trial : trials) {
            if (trial.status() == ClinicalTrialStatus.RECRUITING) {
                recruitingCount++;
            } else if (trial.status() == ClinicalTrialStatus.COMPLETED) {
                completedCount++;
            }
            if (trial.phase() == ClinicalTrialPhase.PHASE_2) {
                phase2Count++;
            } else if (trial.phase() == ClinicalTrialPhase.PHASE_3) {
                phase3Count++;
            }
        }

        model.addAttribute("trials", trials);
        model.addAttribute("startDates", startDates);
        model.addAttribute("discoveredDates", discoveredDates);
        model.addAttribute("trialCount", trials.size());
        model.addAttribute("recruitingCount", recruitingCount);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("phase2Count", phase2Count);
        model.addAttribute("phase3Count", phase3Count);
        model.addAttribute("filter", filter);
        model.addAttribute("fullAccess", fullAccess);

        log.debug("clinicalTrials() | return=clinical-trials, trialCount={}", trials.size());
        return "clinical-trials";
    }

    /**
     * Triggers an on-demand harvest of clinical trials (ADMIN only).
     */
    @PostMapping("/monitoring/clinical-trials-harvest")
    public String triggerHarvest() {
        log.debug("triggerHarvest()");

        int newCount = clinicalTrialsUseCase.triggerHarvest();
        log.info("triggerHarvest() | harvested {} new trials", newCount);

        log.debug("triggerHarvest() | return=redirect");
        return "redirect:/dashboard/clinical-trials";
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
