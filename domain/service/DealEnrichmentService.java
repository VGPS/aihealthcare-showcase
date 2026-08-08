package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.DealContext;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanySentimentPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrameworkAnalysisPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Domain service that enriches a {@link DealSignal} with cross-referenced
 * context from sentiment, framework, regulatory, and company profile data.
 *
 * <p>All lookups are best-effort: if a cross-reference port is null or
 * returns no data, the corresponding field in the returned
 * {@link DealContext} is null or empty. This service has zero framework
 * dependencies — all ports are constructor-injected and nullable.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
public class DealEnrichmentService {

    private static final int MAX_REGULATORY_EVENTS = 10;

    private final CompanySentimentPort sentimentPort;
    private final FrameworkAnalysisPort frameworkPort;
    private final RegulatoryEventPort regulatoryPort;
    private final CompanyProfilePort profilePort;

    public DealEnrichmentService(CompanySentimentPort sentimentPort,
                                  FrameworkAnalysisPort frameworkPort,
                                  RegulatoryEventPort regulatoryPort,
                                  CompanyProfilePort profilePort) {
        this.sentimentPort = sentimentPort;
        this.frameworkPort = frameworkPort;
        this.regulatoryPort = regulatoryPort;
        this.profilePort = profilePort;
    }

    /**
     * Enriches a deal signal with cross-referenced context.
     */
    public DealContext enrich(DealSignal signal) {
        String companyName = signal.companyName();
        String slug = toSlug(companyName);

        CompanySentiment sentiment = lookupSentiment(slug);
        FrameworkAnalysis framework = lookupFramework(slug);
        List<RegulatoryEvent> regulatoryEvents = lookupRegulatory(companyName);
        CompanyProfile profile = lookupProfile(slug);

        return new DealContext(signal, sentiment, framework, regulatoryEvents, profile);
    }

    private CompanySentiment lookupSentiment(String slug) {
        if (sentimentPort == null || slug == null) {
            return null;
        }
        Optional<CompanySentiment> found = sentimentPort.findBySlug(slug);
        return found.orElse(null);
    }

    private FrameworkAnalysis lookupFramework(String slug) {
        if (frameworkPort == null || slug == null) {
            return null;
        }
        Optional<FrameworkAnalysis> found = frameworkPort.findBySlug(slug);
        return found.orElse(null);
    }

    private List<RegulatoryEvent> lookupRegulatory(String companyName) {
        if (regulatoryPort == null || companyName == null || companyName.isBlank()) {
            return List.of();
        }
        List<RegulatoryEvent> byApplicant = regulatoryPort.findByApplicant(companyName, MAX_REGULATORY_EVENTS);
        if (!byApplicant.isEmpty()) {
            return byApplicant;
        }
        return regulatoryPort.findByKeyword(companyName, MAX_REGULATORY_EVENTS);
    }

    private CompanyProfile lookupProfile(String slug) {
        if (profilePort == null || slug == null) {
            return null;
        }
        List<CompanyProfile> all = profilePort.findAll();
        for (CompanyProfile profile : all) {
            if (slug.equals(profile.slug())) {
                return profile;
            }
            String profileSlug = toSlug(profile.name());
            if (slug.equals(profileSlug)) {
                return profile;
            }
        }
        return null;
    }

    private String toSlug(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
