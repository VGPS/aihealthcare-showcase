package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.TrackFrontierClaimsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the Frontier AI Claim Tracker, exposing JSON endpoints
 * for claim retrieval and pipeline triggering at {@code /api/v1/claims}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/claims")
public class ClaimTrackerRestController {

    private final TrackFrontierClaimsUseCase claimsUseCase;
    private final ArticleIngestionPort ingestionPort;

    public ClaimTrackerRestController(TrackFrontierClaimsUseCase claimsUseCase,
                                       ArticleIngestionPort ingestionPort) {
        log.debug("ClaimTrackerRestController() | claimsUseCase={}", claimsUseCase.getClass().getSimpleName());
        this.claimsUseCase = claimsUseCase;
        this.ingestionPort = ingestionPort;
    }

    /**
     * Returns all claims with optional filters.
     *
     * @param company optional company name filter
     * @param verdict optional verdict filter (must match {@link ClaimVerdict#name()})
     * @param type    optional claim type filter (must match {@link ClaimType#name()})
     */
    @GetMapping
    public ResponseEntity<List<FrontierClaim>> getAll(
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) String type) {
        log.debug("getAll() | company={}, verdict={}, type={}", company, verdict, type);

        List<FrontierClaim> result;
        if (company != null && !company.isBlank()) {
            result = claimsUseCase.getByCompany(company);
        } else if (verdict != null && !verdict.isBlank()) {
            result = claimsUseCase.getByVerdict(ClaimVerdict.valueOf(verdict));
        } else if (type != null && !type.isBlank()) {
            result = claimsUseCase.getByType(ClaimType.valueOf(type));
        } else {
            result = claimsUseCase.getAll();
        }

        log.debug("getAll() | return={} claims", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Returns a single claim by its identifier.
     */
    @GetMapping("/{claimId}")
    public ResponseEntity<FrontierClaim> getById(@PathVariable String claimId) {
        log.debug("getById() | claimId={}", claimId);
        return claimsUseCase.getById(claimId)
                .map(c -> {
                    log.debug("getById() | return=present");
                    return ResponseEntity.ok(c);
                })
                .orElseGet(() -> {
                    log.debug("getById() | return=notFound");
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Triggers claim detection against the last 24 hours of articles (ADMIN).
     */
    @PostMapping("/detect")
    public ResponseEntity<List<FrontierClaim>> detect() {
        log.debug("detect()");
        List<NewsArticle> articles = ingestionPort.fetchRecentArticles(1);
        List<FrontierClaim> result = claimsUseCase.detectClaims(articles);
        log.debug("detect() | return={} new claims", result.size());
        return ResponseEntity.ok(result);
    }
}
