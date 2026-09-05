package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyCallType;
import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.model.PerplexityCitation;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyResearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PerplexityCitationPort;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure domain service orchestrating the Perplexity-powered company discovery
 * pipeline: discover → deduplicate → extract fields → cross-validate → persist.
 *
 * <p>This service has no Spring dependencies — it is wired via {@code AppConfig}
 * and operates entirely on domain types and ports.
 *
 * <p>Pipeline phases:
 * <ol>
 *   <li><strong>Discovery</strong> — broad Perplexity query finds candidate company names</li>
 *   <li><strong>Dedup</strong> — check each name against DB by normalized name/domain</li>
 *   <li><strong>Extraction</strong> — structured JSON extraction for new companies</li>
 *   <li><strong>Validation</strong> — cross-check against CB Insights, Rock Health lists</li>
 *   <li><strong>Persist</strong> — save company + all citations to DB</li>
 * </ol>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
public class PerplexityCompanyDiscoveryService {

    private static final System.Logger log =
            System.getLogger(PerplexityCompanyDiscoveryService.class.getName());

    private static final String DISCOVERY_PROMPT =
            "Find all notable companies currently operating in AI-powered healthcare. " +
            "Include clinical AI, diagnostics AI, health AI copilots, digital health AI startups, " +
            "medical imaging AI, revenue cycle management AI, clinical documentation AI, " +
            "drug discovery AI, and healthcare infrastructure AI companies. " +
            "For each company, provide the company name and a one-sentence description. " +
            "Focus on companies founded or significantly active in the last 5 years. " +
            "Include both well-funded startups and established players pivoting to AI healthcare. " +
            "Cite your sources.";

    /** Suffixes stripped during name normalization for dedup — longest first to avoid partial matches. */
    private static final String[] NAME_SUFFIXES = {
            ", inc.", ", inc", ", llc", ", corp", ", ltd",
            " inc.", " inc", " llc.", " llc", " corp.", " corp",
            " ltd.", " ltd", " co.", " co", " plc.", " plc"
    };

    private final CompanyResearchPort researchPort;
    private final HealthcareAiCompanyPort companyPort;
    private final PerplexityCitationPort citationPort;
    private final HealthcareAiCompanyClassifier classifier;
    private final int maxCompaniesPerRun;

    public PerplexityCompanyDiscoveryService(CompanyResearchPort researchPort,
                                              HealthcareAiCompanyPort companyPort,
                                              PerplexityCitationPort citationPort,
                                              HealthcareAiCompanyClassifier classifier,
                                              int maxCompaniesPerRun) {
        this.researchPort = researchPort;
        this.companyPort = companyPort;
        this.citationPort = citationPort;
        this.classifier = classifier;
        this.maxCompaniesPerRun = maxCompaniesPerRun;
    }

    /**
     * Runs the full discovery cycle: discover → dedup → extract → validate → persist.
     *
     * @return number of new companies persisted
     */
    public int runDiscoveryCycle() {
        if (!researchPort.isAvailable()) {
            return 0;
        }

        // Phase 1: Discovery
        CompanyResearchPort.DiscoveryResult discovery = researchPort.discoverCompanies(DISCOVERY_PROMPT);

        // Log discovery citations
        logCitations(null, discovery.citations(), CompanyCallType.DISCOVERY,
                discovery.rawContent());

        // Phase 2: Dedup — filter out companies already in DB
        List<String> newNames = new ArrayList<>();
        for (String name : discovery.companyNames()) {
            String normalized = normalizeName(name);
            if (!companyPort.existsByNameOrDomain(normalized, null)) {
                newNames.add(name);
            }
        }

        // Limit to max per run
        if (newNames.size() > maxCompaniesPerRun) {
            newNames = new ArrayList<>(newNames.subList(0, maxCompaniesPerRun));
        }

        // Phases 3-5: Extract, validate, and persist each new company
        int persisted = 0;
        for (String name : newNames) {
            try {
                HealthcareAiCompany company = processCompany(name);
                if (company != null) {
                    companyPort.save(company);
                    persisted++;
                }
            } catch (Exception e) {
                log.log(System.Logger.Level.WARNING,
                        () -> "runDiscoveryCycle() | failed to process/persist company '" + name
                                + "' — skipping. cause=" + e.getMessage());
            }
        }

        return persisted;
    }

    /**
     * Processes a single company: extract fields → validate → build record.
     */
    HealthcareAiCompany processCompany(String companyName) {
        Instant now = Instant.now();
        String companyId = UUID.randomUUID().toString();

        // Phase 3: Structured extraction
        CompanyResearchPort.ExtractionResult extraction =
                researchPort.extractCompanyFields(companyName);

        // Log extraction citations
        logCitations(companyId, extraction.citations(), CompanyCallType.EXTRACTION, null);

        // Phase 4: Cross-validation
        CompanyResearchPort.ValidationResult validation =
                researchPort.crossValidate(companyName);

        // Log validation citations
        logCitations(companyId, validation.validationSources(), CompanyCallType.VALIDATION,
                validation.details());

        // Phase 5: Build domain record from extracted fields
        Map<String, Object> fields = extraction.fields();
        if (fields == null || fields.isEmpty()) {
            return null;
        }

        String name = getStringField(fields, "name", companyName);
        String normalized = normalizeName(name);

        // Double-check dedup after extraction (name may differ from discovery)
        String domain = getStringField(fields, "domain", null);
        if (companyPort.existsByNameOrDomain(normalized, domain)) {
            return null;
        }

        String description = getStringField(fields, "description", "");
        String subSector = getStringField(fields, "subSector", null);
        String category = classifier.classify(name, description, subSector);

        return new HealthcareAiCompany(
                companyId,
                name,
                normalized,
                domain,
                description,
                getStringField(fields, "hqLocation", null),
                getIntegerField(fields, "foundedYear"),
                getStringField(fields, "sector", "Healthcare AI"),
                subSector,
                category,
                getStringField(fields, "fundingStage", null),
                getStringField(fields, "estimatedFunding", null),
                getStringField(fields, "foundersJson", null),
                extraction.citations(),
                validation.validated(),
                validation.validationSources(),
                now,
                now
        );
    }

    /**
     * Normalizes a company name for deduplication: lowercase, strip legal
     * suffixes (Inc, LLC, Corp, Ltd), trim whitespace.
     */
    public String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String lower = name.toLowerCase().trim();
        for (String suffix : NAME_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                lower = lower.substring(0, lower.length() - suffix.length()).trim();
                break;
            }
        }
        return lower;
    }

    /**
     * Extracts a domain from a URL string (e.g. "https://tempus.com/about" → "tempus.com").
     */
    String extractDomain(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null) {
                return host.startsWith("www.") ? host.substring(4) : host;
            }
        } catch (Exception ignored) {
            // Not a valid URL
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void logCitations(String companyId, List<String> urls,
                              CompanyCallType callType, String context) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<PerplexityCitation> citations = new ArrayList<>();
        for (String url : urls) {
            citations.add(new PerplexityCitation(
                    UUID.randomUUID().toString(),
                    companyId,
                    url,
                    context != null && context.length() > 500
                            ? context.substring(0, 500) : context,
                    callType,
                    now
            ));
        }
        citationPort.saveAll(citations);
    }

    private String getStringField(Map<String, Object> fields, String key, String defaultValue) {
        Object value = fields.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return defaultValue;
    }

    private Integer getIntegerField(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // Not a valid integer
            }
        }
        return null;
    }
}
