package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.port.inbound.TrackFrontierClaimsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thymeleaf controller for the Frontier AI Claim Tracker dashboard at
 * {@code GET /dashboard/claims}.
 *
 * <p>FREE-tier users see at most {@value #FREE_LIMIT} claims; SUBSCRIBER,
 * DEMO, and ADMIN users see all. Filtering by company, verdict, and claim
 * type is applied before the tier cap.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
@Slf4j
@Controller
@RequestMapping("/dashboard/claims")
public class ClaimTrackerController {

    static final int FREE_LIMIT = 5;

    private final TrackFrontierClaimsUseCase claimsUseCase;
    private final TierResolver tierResolver;

    public ClaimTrackerController(TrackFrontierClaimsUseCase claimsUseCase,
                                   TierResolver tierResolver) {
        log.debug("ClaimTrackerController() | claimsUseCase={}", claimsUseCase.getClass().getSimpleName());
        this.claimsUseCase = claimsUseCase;
        this.tierResolver = tierResolver;
    }

    /**
     * Renders the claim tracker list page.
     *
     * @param company optional company name filter (case-insensitive)
     * @param verdict optional verdict filter
     * @param type    optional claim type filter
     */
    @GetMapping
    public String claimTracker(
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) String type,
            Principal principal,
            Model model) {

        log.debug("claimTracker() | company={}, verdict={}, type={}", company, verdict, type);

        List<FrontierClaim> claims = fetchFiltered(company, verdict, type);

        boolean fullAccess = tierResolver.hasFullAccess(principal);
        if (!fullAccess && claims.size() > FREE_LIMIT) {
            claims = claims.subList(0, FREE_LIMIT);
        }

        Set<String> companies = extractCompanies(claimsUseCase.getAll());
        Map<ClaimVerdict, Long> verdictCounts = buildVerdictCounts(claimsUseCase.getAll());
        long totalUnverified = verdictCounts.getOrDefault(ClaimVerdict.ALLEGED_UNVERIFIED, 0L)
                + verdictCounts.getOrDefault(ClaimVerdict.MARKETING_HYPE, 0L)
                + verdictCounts.getOrDefault(ClaimVerdict.CONTRADICTED, 0L);

        model.addAttribute("claims", claims);
        model.addAttribute("companies", companies);
        model.addAttribute("verdictCounts", verdictCounts);
        model.addAttribute("totalUnverified", totalUnverified);
        model.addAttribute("claimTypes", Arrays.asList(ClaimType.values()));
        model.addAttribute("verdicts", Arrays.asList(ClaimVerdict.values()));
        model.addAttribute("filterCompany", company);
        model.addAttribute("filterVerdict", verdict);
        model.addAttribute("filterType", type);
        model.addAttribute("fullAccess", fullAccess);
        model.addAttribute("freeLimit", FREE_LIMIT);
        model.addAttribute("activePage", "claims");

        log.debug("claimTracker() | return=claim-tracker, claimsShown={}", claims.size());
        return "claim-tracker";
    }

    private List<FrontierClaim> fetchFiltered(String company, String verdict, String type) {
        if (company != null && !company.isBlank()) {
            return claimsUseCase.getByCompany(company);
        }
        if (verdict != null && !verdict.isBlank()) {
            try {
                return claimsUseCase.getByVerdict(ClaimVerdict.valueOf(verdict));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown verdict: " + verdict);
            }
        }
        if (type != null && !type.isBlank()) {
            try {
                return claimsUseCase.getByType(ClaimType.valueOf(type));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown claim type: " + type);
            }
        }
        return claimsUseCase.getAll();
    }

    private Set<String> extractCompanies(List<FrontierClaim> all) {
        return all.stream()
                .map(FrontierClaim::company)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<ClaimVerdict, Long> buildVerdictCounts(List<FrontierClaim> all) {
        Map<ClaimVerdict, Long> counts = new EnumMap<>(ClaimVerdict.class);
        for (ClaimVerdict v : ClaimVerdict.values()) {
            counts.put(v, 0L);
        }
        for (FrontierClaim c : all) {
            counts.merge(c.verdict(), 1L, Long::sum);
        }
        return counts;
    }
}
