package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyDiscoveryResult;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.DiscoverCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.web.dto.CompanyDiscoveryResponse;
import com.wgblackmon.aihealthcare.web.dto.CompanyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for triggering the AI-healthcare startup company
 * discovery pipeline and retrieving results.
 *
 * <p>Exposes a POST endpoint that scrapes YC and TopStartups.io,
 * classifies companies, deduplicates, persists as articles, and
 * returns the categorized company list with newsletter markdown.
 *
 * <p>This is a <strong>Member-only</strong> feature. Callers must
 * supply an {@code X-Subscriber-Email} header for a MEMBER-tier
 * subscriber, or the request is rejected with HTTP 403.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/companies")
public class CompanyDiscoveryController {

    private final DiscoverCompaniesUseCase discoverCompaniesUseCase;
    private final SubscriberPort subscriberPort;

    public CompanyDiscoveryController(DiscoverCompaniesUseCase discoverCompaniesUseCase,
                                      SubscriberPort subscriberPort) {
        this.discoverCompaniesUseCase = discoverCompaniesUseCase;
        this.subscriberPort = subscriberPort;
        log.debug("CompanyDiscoveryController() | discoverCompaniesUseCase={}, subscriberPort={}",
                discoverCompaniesUseCase.getClass().getSimpleName(),
                subscriberPort.getClass().getSimpleName());
        log.debug("CompanyDiscoveryController() | return=void");
    }

    /**
     * Triggers the full company discovery pipeline.
     *
     * <p>Requires a MEMBER-tier subscriber. Pass the subscriber's email
     * via the {@code X-Subscriber-Email} header.
     *
     * @param subscriberEmail subscriber email from header (required)
     * @return discovery result with companies, markdown, and summary counts
     */
    @PostMapping("/discover")
    public ResponseEntity<?> discover(
            @RequestHeader(value = "X-Subscriber-Email", required = false) String subscriberEmail) {
        log.debug("discover() | subscriberEmail={}", subscriberEmail);

        // Tier gating — MEMBER only
        SubscriptionTier tier = resolveTier(subscriberEmail);
        if (tier != SubscriptionTier.MEMBER) {
            log.warn("discover() | access denied — tier={} for email={}", tier, subscriberEmail);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Company discovery is a Member-only feature");
            body.put("tier", tier.name());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        CompanyDiscoveryResult result = discoverCompaniesUseCase.discover();

        List<CompanyResponse> companyDtos = new ArrayList<>();
        for (Company c : result.companies()) {
            companyDtos.add(new CompanyResponse(
                    c.name(),
                    c.source(),
                    c.url(),
                    c.companySite(),
                    c.description(),
                    c.tags().scribe(),
                    c.tags().agent(),
                    c.tags().imaging(),
                    c.tags().rcm(),
                    c.tags().infra(),
                    c.isAI(),
                    c.isHealth()
            ));
        }

        CompanyDiscoveryResponse response = new CompanyDiscoveryResponse(
                companyDtos,
                result.markdown(),
                result.totalScraped(),
                result.afterDedup(),
                result.aiHealthFiltered()
        );

        log.debug("discover() | return totalScraped={}, afterDedup={}, aiHealthFiltered={}",
                result.totalScraped(), result.afterDedup(), result.aiHealthFiltered());
        return ResponseEntity.ok(response);
    }

    private SubscriptionTier resolveTier(String email) {
        log.debug("resolveTier() | email={}", email);
        if (email == null || email.isBlank()) {
            log.debug("resolveTier() | return={}", SubscriptionTier.FREE);
            return SubscriptionTier.FREE;
        }
        Optional<Subscriber> subscriber = subscriberPort.findByEmail(email);
        SubscriptionTier result = subscriber.map(Subscriber::tier).orElse(SubscriptionTier.FREE);
        log.debug("resolveTier() | return={}", result);
        return result;
    }
}
