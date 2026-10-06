package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.port.inbound.TrackFrontierClaimsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Public controller for the Frontier AI Claims Methodology page.
 *
 * Renders a fully public, linkable page at /claims/methodology that explains
 * how claims are detected, how each of the five verdicts is assigned, and why
 * the distribution matters. Includes live verdict counts drawn directly from
 * the tracker database so the page always reflects current state.
 *
 * This page requires no login and is intended to be cited by journalists,
 * compliance teams, and researchers who encounter a claim verdict and want
 * to understand the evidentiary standard behind it.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-06
 * @updated 2026-10-06
 */
@Slf4j
@Controller
@RequestMapping("/claims/methodology")
public class ClaimsMethodologyController {

    private static final DateTimeFormatter SINCE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy")
                             .withZone(ZoneId.of("America/Chicago"));

    private final TrackFrontierClaimsUseCase claimsUseCase;

    public ClaimsMethodologyController(TrackFrontierClaimsUseCase claimsUseCase) {
        this.claimsUseCase = claimsUseCase;
    }

    @GetMapping
    public String methodology(Model model) {
        log.debug("methodology() | building public methodology page");

        List<FrontierClaim> all = claimsUseCase.getAll();

        // Verdict counts
        Map<ClaimVerdict, Long> verdictCounts = Arrays.stream(ClaimVerdict.values())
                .collect(Collectors.toMap(v -> v,
                        v -> all.stream().filter(c -> c.verdict() == v).count(),
                        (a, b) -> a,
                        () -> new EnumMap<>(ClaimVerdict.class)));

        // Claim type counts
        Map<ClaimType, Long> typeCounts = Arrays.stream(ClaimType.values())
                .collect(Collectors.toMap(t -> t,
                        t -> all.stream().filter(c -> c.claimType() == t).count(),
                        (a, b) -> a,
                        () -> new EnumMap<>(ClaimType.class)));

        // Companies with any claim
        long companyCount = all.stream()
                .map(FrontierClaim::company)
                .filter(c -> !c.toLowerCase().startsWith("no_qualifying"))
                .distinct()
                .count();

        // Tracking since: earliest detectedAt
        String trackingSince = all.stream()
                .map(FrontierClaim::detectedAt)
                .min(Comparator.naturalOrder())
                .map(SINCE_FMT::format)
                .orElse("recently");

        // Most recent Marketing Hype claim (for the "latest flag" callout)
        Optional<FrontierClaim> latestHype = claimsUseCase
                .getByVerdict(ClaimVerdict.MARKETING_HYPE)
                .stream()
                .findFirst(); // already ordered detectedAt DESC

        model.addAttribute("verdictCounts", verdictCounts);
        model.addAttribute("typeCounts", typeCounts);
        model.addAttribute("total", all.size());
        model.addAttribute("companyCount", companyCount);
        model.addAttribute("trackingSince", trackingSince);
        model.addAttribute("latestHype", latestHype.orElse(null));
        model.addAttribute("activePage", "claimsMethodology");

        log.debug("methodology() | return=view total={} verdictCounts={}", all.size(), verdictCounts);
        return "claims-methodology";
    }
}
