package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialPhase;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorClinicalTrialsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineAsyncRunner;
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
import java.util.ArrayList;
import java.util.Comparator;
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
 * @updated 2026-09-08
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
    private final PipelineAsyncRunner asyncRunner;

    public ClinicalTrialController(MonitorClinicalTrialsUseCase clinicalTrialsUseCase,
                                    SubscriberPort subscriberPort,
                                    PipelineAsyncRunner asyncRunner) {
        log.debug("ClinicalTrialController() | clinicalTrialsUseCase={}, subscriberPort={}, asyncRunner={}",
                  clinicalTrialsUseCase, subscriberPort, asyncRunner);
        this.clinicalTrialsUseCase = clinicalTrialsUseCase;
        this.subscriberPort = subscriberPort;
        this.asyncRunner = asyncRunner;
    }

    /**
     * Renders the clinical trials page.
     *
     * @param filter    optional filter: "recruiting", "completed", or null for all
     * @param sort      optional sort column: "status" (default), "title", "sponsor",
     *                  "phase", "conditions", or "nctid"
     * @param principal the authenticated user, or null for anonymous
     * @param model     Thymeleaf model
     * @return the "clinical-trials" view name
     */
    @GetMapping("/dashboard/clinical-trials")
    public String clinicalTrials(@RequestParam(required = false) String filter,
                                  @RequestParam(required = false, defaultValue = "status") String sort,
                                  Principal principal,
                                  Model model) {
        log.debug("clinicalTrials() | filter={}, sort={}, principal={}", filter, sort,
                  principal != null ? principal.getName() : "anonymous");

        SubscriptionTier tier = resolveTier(principal);
        boolean fullAccess = tier == SubscriptionTier.SUBSCRIBER
                || tier == SubscriptionTier.DEMO
                || tier == SubscriptionTier.ENTERPRISE
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

        // Sort the results
        trials = sortTrials(trials, sort);

        // Format dates server-side
        Map<String, String> startDates = new HashMap<>();
        for (ClinicalTrial trial : trials) {
            if (trial.startDate() != null) {
                startDates.put(trial.trialId(), DISPLAY_FMT.format(trial.startDate()));
            }
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
        model.addAttribute("trialCount", trials.size());
        model.addAttribute("recruitingCount", recruitingCount);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("phase2Count", phase2Count);
        model.addAttribute("phase3Count", phase3Count);
        model.addAttribute("filter", filter);
        model.addAttribute("sort", sort);
        model.addAttribute("fullAccess", fullAccess);

        log.debug("clinicalTrials() | return=clinical-trials, trialCount={}", trials.size());
        return "clinical-trials";
    }

    /**
     * Sorts the trials list by the specified column.
     *
     * @param trials the unsorted trial list
     * @param sort   column name with optional _desc suffix (e.g. "title", "title_desc")
     * @return a new sorted list
     */
    private List<ClinicalTrial> sortTrials(List<ClinicalTrial> trials, String sort) {
        log.debug("sortTrials() | sort={}, size={}", sort, trials.size());

        if (trials.isEmpty()) {
            log.debug("sortTrials() | return=empty list");
            return trials;
        }

        boolean descending = sort != null && sort.endsWith("_desc");
        String column = descending ? sort.substring(0, sort.length() - 5) : sort;

        Comparator<ClinicalTrial> comparator;
        if ("title".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(ClinicalTrial::title, String.CASE_INSENSITIVE_ORDER);
        } else if ("sponsor".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    t -> t.sponsor() != null ? t.sponsor() : "",
                    String.CASE_INSENSITIVE_ORDER);
        } else if ("phase".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    t -> t.phase() != null ? t.phase().ordinal() : Integer.MAX_VALUE);
        } else if ("conditions".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    t -> t.conditions().isEmpty() ? "" : t.conditions().get(0),
                    String.CASE_INSENSITIVE_ORDER);
        } else if ("nctid".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(ClinicalTrial::nctId);
        } else {
            // Default: status
            comparator = Comparator.comparing(t -> t.status().name());
        }

        if (descending) {
            comparator = comparator.reversed();
        }

        List<ClinicalTrial> sorted = new ArrayList<>(trials);
        sorted.sort(comparator);

        log.debug("sortTrials() | return=sorted list, size={}", sorted.size());
        return sorted;
    }

    /**
     * Triggers an on-demand harvest of clinical trials (ADMIN only).
     */
    @PostMapping("/monitoring/clinical-trials-harvest")
    public String triggerHarvest() {
        log.debug("triggerHarvest()");
        asyncRunner.runAsync("clinical-trials", () -> clinicalTrialsUseCase.triggerHarvest());
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
