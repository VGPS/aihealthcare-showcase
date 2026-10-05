package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.model.SocialPlatform;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSavedPostsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.TrackFrontierClaimsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thymeleaf controller for the Frontier AI Claim Tracker dashboard at
 * {@code GET /dashboard/claims}.
 *
 * <p>Renders a sortable table of LLM-extracted claims from frontier AI companies,
 * color-coded by evidence quality verdict. Supports server-side sorting by company,
 * verdict, type, claimDate, and detectedAt. FREE-tier users see at most
 * {@value #FREE_LIMIT} claims; SUBSCRIBER, DEMO, and ADMIN users see all.
 *
 * <p>Social post drafts can be generated per claim via
 * {@code POST /dashboard/claims/{claimId}/generate-post}, which creates both
 * LinkedIn and Facebook drafts via {@link ManageSavedPostsUseCase}.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-10-05
 * @updated 2026-10-05
 */
@Slf4j
@Controller
@RequestMapping("/dashboard/claims")
public class ClaimTrackerController {

    static final int FREE_LIMIT = 5;

    private static final DateTimeFormatter CLAIM_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z")
                             .withZone(ZoneId.of("America/Chicago"));

    private final TrackFrontierClaimsUseCase claimsUseCase;
    private final ManageSavedPostsUseCase savedPostsUseCase;
    private final TierResolver tierResolver;

    public ClaimTrackerController(TrackFrontierClaimsUseCase claimsUseCase,
                                   ManageSavedPostsUseCase savedPostsUseCase,
                                   TierResolver tierResolver) {
        log.debug("ClaimTrackerController() | claimsUseCase={}", claimsUseCase.getClass().getSimpleName());
        this.claimsUseCase = claimsUseCase;
        this.savedPostsUseCase = savedPostsUseCase;
        this.tierResolver = tierResolver;
    }

    /**
     * Renders the claim tracker sortable table.
     *
     * @param company optional company name filter (case-insensitive)
     * @param verdict optional verdict filter
     * @param type    optional claim type filter
     * @param sort    sort column: company, verdict, type, claimDate, detectedAt (default)
     * @param dir     sort direction: asc or desc (default: desc for detectedAt, asc for others)
     */
    @GetMapping
    public String claimTracker(
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            Principal principal,
            Model model) {

        log.debug("claimTracker() | company={}, verdict={}, type={}, sort={}, dir={}",
                  company, verdict, type, sort, dir);

        List<FrontierClaim> claims = fetchFiltered(company, verdict, type);
        claims = applySort(claims, sort, dir);

        boolean fullAccess = tierResolver.hasFullAccess(principal);
        if (!fullAccess && claims.size() > FREE_LIMIT) {
            claims = claims.subList(0, FREE_LIMIT);
        }

        String effectiveSort = (sort == null || sort.isBlank()) ? "detectedAt" : sort;
        String effectiveDir = resolveDir(effectiveSort, dir);

        List<FrontierClaim> all = claimsUseCase.getAll();
        Set<String> companies = extractCompanies(all);
        Map<ClaimVerdict, Long> verdictCounts = buildVerdictCounts(all);
        long totalUnverified = verdictCounts.getOrDefault(ClaimVerdict.ALLEGED_UNVERIFIED, 0L)
                + verdictCounts.getOrDefault(ClaimVerdict.MARKETING_HYPE, 0L)
                + verdictCounts.getOrDefault(ClaimVerdict.CONTRADICTED, 0L);

        Map<String, String> claimTimestamps = new HashMap<>();
        for (FrontierClaim c : claims) {
            claimTimestamps.put(c.claimId(), CLAIM_FMT.format(c.detectedAt()));
        }

        model.addAttribute("claims", claims);
        model.addAttribute("companies", companies);
        model.addAttribute("verdictCounts", verdictCounts);
        model.addAttribute("totalUnverified", totalUnverified);
        model.addAttribute("claimTypes", Arrays.asList(ClaimType.values()));
        model.addAttribute("verdicts", Arrays.asList(ClaimVerdict.values()));
        model.addAttribute("filterCompany", company);
        model.addAttribute("filterVerdict", verdict);
        model.addAttribute("filterType", type);
        model.addAttribute("sortField", effectiveSort);
        model.addAttribute("sortDir", effectiveDir);
        model.addAttribute("fullAccess", fullAccess);
        model.addAttribute("freeLimit", FREE_LIMIT);
        model.addAttribute("claimTimestamps", claimTimestamps);
        model.addAttribute("activePage", "claims");

        log.debug("claimTracker() | return=claim-tracker, claimsShown={}", claims.size());
        return "claim-tracker";
    }

    /**
     * Generates LinkedIn and Facebook draft posts from a single FrontierClaim
     * and redirects to the social post drafts queue.
     *
     * @param claimId the claim to generate posts for
     */
    @PostMapping("/{claimId}/generate-post")
    public String generatePost(@PathVariable String claimId,
                               RedirectAttributes redirectAttrs) {
        log.debug("generatePost() | claimId={}", claimId);

        Optional<FrontierClaim> found = claimsUseCase.getById(claimId);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found: " + claimId);
        }
        FrontierClaim claim = found.get();

        String dateRef = claim.claimDate() != null ? claim.claimDate().toString() : "";
        String verdictLabel = claim.verdict().name().replace('_', ' ');
        String typeLabel = claim.claimType().name().replace('_', ' ');

        StringBuilder body = new StringBuilder();
        body.append(claim.company()).append(" — ").append(typeLabel).append("\n\n");
        body.append(claim.claimText());
        body.append("\n\nVerdict: ").append(verdictLabel);
        if (claim.evidenceNotes() != null && !claim.evidenceNotes().isBlank()) {
            body.append("\n\n").append(claim.evidenceNotes());
        }
        if (claim.sourceTitle() != null && !claim.sourceTitle().isBlank()) {
            body.append("\n\n→ ").append(claim.sourceTitle());
        }
        if (claim.sourceUrl() != null && !claim.sourceUrl().isBlank()) {
            body.append("\n").append(claim.sourceUrl());
        }

        String bodyText = body.toString();
        savedPostsUseCase.save(SocialPlatform.LINKEDIN, dateRef, bodyText, null, "");
        savedPostsUseCase.save(SocialPlatform.FACEBOOK, dateRef, bodyText, null, "");

        redirectAttrs.addFlashAttribute("successMsg",
                "LinkedIn and Facebook drafts created for: " + claim.company());
        log.debug("generatePost() | return=redirect:/dashboard/social/drafts");
        return "redirect:/dashboard/social/drafts";
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

    private List<FrontierClaim> applySort(List<FrontierClaim> claims, String sort, String dir) {
        String effectiveSort = (sort == null || sort.isBlank()) ? "detectedAt" : sort;
        boolean descending;
        if (dir == null || dir.isBlank()) {
            descending = "detectedAt".equals(effectiveSort);
        } else {
            descending = "desc".equalsIgnoreCase(dir);
        }
        Comparator<FrontierClaim> comparator = buildComparator(effectiveSort);
        if (descending) {
            comparator = comparator.reversed();
        }
        return claims.stream().sorted(comparator).collect(Collectors.toList());
    }

    private Comparator<FrontierClaim> buildComparator(String sort) {
        if ("company".equals(sort)) {
            return Comparator.comparing(FrontierClaim::company, String.CASE_INSENSITIVE_ORDER);
        } else if ("verdict".equals(sort)) {
            return Comparator.comparing((FrontierClaim c) -> c.verdict().name());
        } else if ("type".equals(sort)) {
            return Comparator.comparing((FrontierClaim c) -> c.claimType().name());
        } else if ("claimDate".equals(sort)) {
            return Comparator.comparing(FrontierClaim::claimDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        }
        return Comparator.comparing(FrontierClaim::detectedAt);
    }

    private String resolveDir(String sortField, String dir) {
        if (dir != null && !dir.isBlank()) {
            return dir.toLowerCase();
        }
        return "detectedAt".equals(sortField) ? "desc" : "asc";
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
