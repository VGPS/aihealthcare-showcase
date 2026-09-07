package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.DealTerms;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.DealTermsPort;
import com.wgblackmon.aihealthcare.web.dto.DealTermsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * REST controller exposing deal terms endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/market-digest/deal-terms/{headline}} — deal terms for the
 *       given digest entry headline (URL-decoded); 404 if no terms exist</li>
 * </ul>
 *
 * <p>All endpoints require authentication ({@code /api/**} rule in SecurityConfig).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
@Slf4j
@RestController
@RequestMapping("/api/market-digest/deal-terms")
public class DealTermsController {

    private final DealTermsPort dealTermsPort;

    public DealTermsController(DealTermsPort dealTermsPort) {
        log.debug("DealTermsController() | dealTermsPort={}", dealTermsPort);
        this.dealTermsPort = dealTermsPort;
        log.debug("DealTermsController() | return=void");
    }

    /**
     * Returns deal terms for the given digest entry headline.
     *
     * @param headline the M&amp;A entry headline (URL-decoded from path)
     * @return 200 with deal terms body, or 404 if no terms exist for the headline
     */
    @GetMapping("/{headline}")
    public ResponseEntity<DealTermsResponse> getByHeadline(@PathVariable String headline) {
        String decodedHeadline = URLDecoder.decode(headline, StandardCharsets.UTF_8);
        log.debug("getByHeadline() | headline={}", decodedHeadline);

        Optional<DealTerms> terms = dealTermsPort.findByEntryHeadline(decodedHeadline);

        if (terms.isEmpty()) {
            log.debug("getByHeadline() | return=404");
            return ResponseEntity.notFound().build();
        }

        DealTermsResponse response = toResponse(terms.get());
        log.debug("getByHeadline() | return=200, disclosedPortion={}", response.disclosedPortion());
        return ResponseEntity.ok(response);
    }

    // --- mapping helpers ────────────────────────────────────────────────────

    private DealTermsResponse toResponse(DealTerms d) {
        return new DealTermsResponse(
                d.upfrontCashUsd(),
                d.milestonePaymentsUsd(),
                d.equityStakePct(),
                d.royaltyPct(),
                d.disclosedPortion().name(),
                d.sourceUrl()
        );
    }
}
