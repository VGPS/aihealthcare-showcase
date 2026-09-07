package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.LawChangeEvent;
import com.wgblackmon.aihealthcare.domain.model.LawStatus;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageStateLawsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Thymeleaf controller for the State Health-AI Legislation Registry.
 *
 * <p>Serves four views: a public searchable index, an authenticated detail
 * page, a state-by-state map summary, and an upcoming effective dates page.
 *
 * <p>Tier gating: the index page at {@code /legislation} is public (no
 * login required). Detail, map, and upcoming pages require authentication.
 * FREE users can view up to 5 law detail pages; SUBSCRIBER/DEMO/ENTERPRISE/ADMIN
 * users have full access.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
@Slf4j
@Controller
@RequestMapping("/legislation")
public class StateLawController {

    private static final int FREE_DETAIL_LIMIT = 5;

    private final ManageStateLawsUseCase legislationUseCase;
    private final SubscriberPort subscriberPort;

    public StateLawController(ManageStateLawsUseCase legislationUseCase,
                              SubscriberPort subscriberPort) {
        log.debug("StateLawController() | legislationUseCase={}, subscriberPort={}",
                  legislationUseCase.getClass().getSimpleName(),
                  subscriberPort.getClass().getSimpleName());
        this.legislationUseCase = legislationUseCase;
        this.subscriberPort = subscriberPort;
    }

    /**
     * Renders the public legislation index page with filter and search support.
     *
     * @param state    optional state code filter (e.g. "CA")
     * @param category optional category filter (e.g. "PAYER_UTILIZATION_REVIEW")
     * @param status   optional status filter (e.g. "ENACTED")
     * @param q        optional search query
     * @param model    Thymeleaf model
     * @return the "legislation-index" view name
     */
    @GetMapping
    public String index(@RequestParam(required = false) String state,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String q,
                        Model model) {
        log.debug("index() | state={}, category={}, status={}, q={}", state, category, status, q);

        List<StateLaw> laws;
        if (q != null && !q.isBlank()) {
            laws = legislationUseCase.search(q);
        } else if (state != null && !state.isBlank()) {
            laws = legislationUseCase.getByState(StateCode.valueOf(state));
        } else if (category != null && !category.isBlank()) {
            laws = legislationUseCase.getByCategory(LawCategory.valueOf(category));
        } else if (status != null && !status.isBlank()) {
            laws = legislationUseCase.getByStatus(LawStatus.valueOf(status));
        } else {
            laws = legislationUseCase.getAll();
        }

        // Sort by yearEnacted descending, then stateName ascending
        List<StateLaw> sorted = new ArrayList<>(laws);
        sorted.sort(Comparator.comparingInt(StateLaw::yearEnacted).reversed()
                .thenComparing(l -> l.stateName() != null ? l.stateName() : ""));

        // Compute summary counts
        int totalLaws = sorted.size();
        int enactedCount = 0;
        Set<StateCode> distinctStates = new HashSet<>();
        for (StateLaw law : sorted) {
            if (law.status() == LawStatus.ENACTED || law.status() == LawStatus.ENACTED_STAYED) {
                enactedCount++;
            }
            distinctStates.add(law.stateCode());
        }
        int stateCount = distinctStates.size();

        // Format dates server-side
        Map<String, String> formattedDates = buildFormattedDates(sorted);

        model.addAttribute("laws", sorted);
        model.addAttribute("totalLaws", totalLaws);
        model.addAttribute("enactedCount", enactedCount);
        model.addAttribute("stateCount", stateCount);
        model.addAttribute("stateFilter", state);
        model.addAttribute("categoryFilter", category);
        model.addAttribute("statusFilter", status);
        model.addAttribute("searchQuery", q);
        model.addAttribute("states", StateCode.values());
        model.addAttribute("categories", LawCategory.values());
        model.addAttribute("statuses", LawStatus.values());
        model.addAttribute("formattedDates", formattedDates);

        log.debug("index() | return=legislation-index, totalLaws={}", totalLaws);
        return "legislation-index";
    }

    /**
     * Renders the law detail page (requires authentication).
     *
     * @param id        the law slug id
     * @param principal the authenticated user
     * @param model     Thymeleaf model
     * @return the "legislation-detail" view name, or "error/404" if not found
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable String id,
                         Principal principal,
                         Model model) {
        log.debug("detail() | id={}, principal={}", id,
                  principal != null ? principal.getName() : "anonymous");

        Optional<StateLaw> found = legislationUseCase.getById(id);
        if (found.isEmpty()) {
            log.debug("detail() | return=redirect:/legislation (not found)");
            return "redirect:/legislation";
        }

        SubscriptionTier tier = resolveTier(principal);
        boolean fullAccess = tier == SubscriptionTier.SUBSCRIBER
                || tier == SubscriptionTier.DEMO
                || tier == SubscriptionTier.ENTERPRISE
                || isAdmin(principal);

        StateLaw law = found.get();
        Map<String, String> formattedDates = buildFormattedDatesForLaw(law);

        model.addAttribute("law", law);
        model.addAttribute("formattedDates", formattedDates);
        model.addAttribute("fullAccess", fullAccess);

        log.debug("detail() | return=legislation-detail, law={}", law.id());
        return "legislation-detail";
    }

    /**
     * Renders the state-by-state summary map page (requires authentication).
     *
     * @param principal the authenticated user
     * @param model     Thymeleaf model
     * @return the "legislation-map" view name
     */
    @GetMapping("/map")
    public String map(Principal principal, Model model) {
        log.debug("map() | principal={}", principal != null ? principal.getName() : "anonymous");

        List<StateLaw> allLaws = legislationUseCase.getAll();

        // Group by stateCode → count
        Map<String, Integer> lawsByState = new HashMap<>();
        Map<String, String> stateNames = new HashMap<>();
        for (StateLaw law : allLaws) {
            String code = law.stateCode().name();
            lawsByState.merge(code, 1, Integer::sum);
            stateNames.put(code, law.stateName());
        }

        model.addAttribute("lawsByState", lawsByState);
        model.addAttribute("stateNames", stateNames);
        model.addAttribute("totalLaws", allLaws.size());

        log.debug("map() | return=legislation-map, states={}", lawsByState.size());
        return "legislation-map";
    }

    /**
     * Renders the upcoming effective dates page (requires authentication).
     *
     * @param principal the authenticated user
     * @param model     Thymeleaf model
     * @return the "legislation-upcoming" view name
     */
    @GetMapping("/upcoming")
    public String upcoming(Principal principal, Model model) {
        log.debug("upcoming() | principal={}", principal != null ? principal.getName() : "anonymous");

        List<StateLaw> upcomingLaws = legislationUseCase.getUpcoming(90);

        // Sort by effectiveDate ascending
        List<StateLaw> sorted = new ArrayList<>(upcomingLaws);
        sorted.sort(Comparator.comparing(
                l -> l.effectiveDate() != null ? l.effectiveDate() : "",
                String::compareTo));

        Map<String, String> formattedDates = buildFormattedDates(sorted);

        model.addAttribute("upcomingLaws", sorted);
        model.addAttribute("formattedDates", formattedDates);

        log.debug("upcoming() | return=legislation-upcoming, count={}", sorted.size());
        return "legislation-upcoming";
    }

    // ── Admin change review ────────────────────────────────────────────────

    /**
     * Renders the admin change-review page showing unreviewed source changes.
     *
     * @param model Thymeleaf model
     * @return the "legislation-changes" view name
     */
    @GetMapping("/changes")
    @PreAuthorize("hasRole('ADMIN')")
    public String changes(Model model) {
        log.debug("changes()");

        List<LawChangeEvent> unreviewedChanges = legislationUseCase.getUnreviewedChanges();
        model.addAttribute("changes", unreviewedChanges);
        model.addAttribute("changeCount", unreviewedChanges.size());

        log.debug("changes() | return=legislation-changes, count={}", unreviewedChanges.size());
        return "legislation-changes";
    }

    /**
     * Marks a change event as reviewed and redirects back to the changes page.
     *
     * @param id the change event id
     * @return redirect to /legislation/changes
     */
    @PostMapping("/changes/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public String reviewChangeAndRedirect(@PathVariable Long id) {
        log.debug("reviewChangeAndRedirect() | id={}", id);
        legislationUseCase.reviewChange(id);
        log.debug("reviewChangeAndRedirect() | return=redirect:/legislation/changes");
        return "redirect:/legislation/changes";
    }

    // ── Tier resolution helpers ──────────────────────────────────────────────

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

    // ── Date formatting helpers ──────────────────────────────────────────────

    /**
     * Builds a map of formatted date strings for a list of laws.
     * Keys: "{lawId}_dateSigned", "{lawId}_effectiveDate"
     */
    private Map<String, String> buildFormattedDates(List<StateLaw> laws) {
        Map<String, String> dates = new HashMap<>();
        for (StateLaw law : laws) {
            if (law.dateSigned() != null && !law.dateSigned().isBlank()) {
                dates.put(law.id() + "_dateSigned", formatIsoDate(law.dateSigned()));
            }
            if (law.effectiveDate() != null && !law.effectiveDate().isBlank()) {
                dates.put(law.id() + "_effectiveDate", formatIsoDate(law.effectiveDate()));
            }
        }
        return dates;
    }

    /**
     * Builds a map of formatted date strings for a single law.
     */
    private Map<String, String> buildFormattedDatesForLaw(StateLaw law) {
        Map<String, String> dates = new HashMap<>();
        if (law.dateSigned() != null && !law.dateSigned().isBlank()) {
            dates.put("dateSigned", formatIsoDate(law.dateSigned()));
        }
        if (law.effectiveDate() != null && !law.effectiveDate().isBlank()) {
            dates.put("effectiveDate", formatIsoDate(law.effectiveDate()));
        }
        return dates;
    }

    /**
     * Formats an ISO date string (e.g. "2026-01-15") to display format (e.g. "Jan 15, 2026").
     */
    private String formatIsoDate(String isoDate) {
        try {
            java.time.LocalDate parsed = java.time.LocalDate.parse(isoDate);
            return parsed.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"));
        } catch (Exception e) {
            log.debug("formatIsoDate() | unparseable date: {}", isoDate);
            return isoDate;
        }
    }
}
