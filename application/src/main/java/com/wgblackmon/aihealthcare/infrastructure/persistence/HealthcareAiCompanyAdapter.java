package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing {@link HealthcareAiCompanyPort} for persisting
 * and querying AI healthcare company records.
 *
 * <p>Source URLs and validation sources are stored as pipe-delimited strings
 * in the database and converted to/from {@code List<String>} on the domain side.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-02
 * @updated 2026-08-27
 */
@Slf4j
@Component
public class HealthcareAiCompanyAdapter implements HealthcareAiCompanyPort {

    private final HealthcareAiCompanyRepository repository;

    public HealthcareAiCompanyAdapter(HealthcareAiCompanyRepository repository) {
        log.debug("HealthcareAiCompanyAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(HealthcareAiCompany company) {
        log.debug("save() | companyId={}, name={}", company.companyId(), company.name());
        repository.save(toEntity(company));
        log.debug("save() | return=void");
    }

    @Override
    public Optional<HealthcareAiCompany> findByNameNormalized(String nameNormalized) {
        log.debug("findByNameNormalized() | nameNormalized={}", nameNormalized);
        Optional<HealthcareAiCompany> result = repository.findByNameNormalized(nameNormalized)
                .map(this::toDomain);
        log.debug("findByNameNormalized() | return={}", result.isPresent() ? "found" : "empty");
        return result;
    }

    @Override
    public Optional<HealthcareAiCompany> findByDomain(String domain) {
        log.debug("findByDomain() | domain={}", domain);
        Optional<HealthcareAiCompany> result = repository.findByDomain(domain)
                .map(this::toDomain);
        log.debug("findByDomain() | return={}", result.isPresent() ? "found" : "empty");
        return result;
    }

    @Override
    public boolean existsByNameOrDomain(String nameNormalized, String domain) {
        log.debug("existsByNameOrDomain() | nameNormalized={}, domain={}", nameNormalized, domain);
        boolean exists = repository.existsByNameNormalized(nameNormalized);
        if (!exists && domain != null && !domain.isBlank()) {
            exists = repository.existsByDomain(domain);
        }
        log.debug("existsByNameOrDomain() | return={}", exists);
        return exists;
    }

    @Override
    public Optional<HealthcareAiCompany> findBySlug(String slug) {
        log.debug("findBySlug() | slug={}", slug);
        // Try the stored slug column first (fast path)
        Optional<HealthcareAiCompany> result = repository.findBySlug(slug).map(this::toDomain);
        if (result.isPresent()) {
            log.debug("findBySlug() | return=found (slug column)");
            return result;
        }
        // Fall back: scan all companies matching by name-derived slug.
        // Handles rows inserted before the slug column existed (slug IS NULL).
        for (HealthcareAiCompanyEntity entity : repository.findAll()) {
            if (slug.equals(computeSlug(entity.getName()))) {
                // Opportunistically backfill the slug column so next lookup is fast
                entity.setSlug(slug);
                repository.save(entity);
                log.debug("findBySlug() | return=found (name fallback, slug backfilled)");
                return Optional.of(toDomain(entity));
            }
        }
        log.debug("findBySlug() | return=empty");
        return Optional.empty();
    }

    @Override
    public List<HealthcareAiCompany> findAll() {
        log.debug("findAll() |");
        List<HealthcareAiCompanyEntity> entities = repository.findAllByOrderByDiscoveredAtDesc();
        List<HealthcareAiCompany> result = new ArrayList<>();
        for (HealthcareAiCompanyEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findAll() | return={} companies", result.size());
        return result;
    }

    @Override
    public List<HealthcareAiCompany> findAllByOrderByName() {
        log.debug("findAllByOrderByName() |");
        List<HealthcareAiCompanyEntity> entities = repository.findAllByOrderByNameAsc();
        List<HealthcareAiCompany> result = new ArrayList<>();
        for (HealthcareAiCompanyEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findAllByOrderByName() | return={} companies", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    /** Converts a display name to a URL-safe slug (e.g. "Grelin Health" → "grelin-health"). */
    static String computeSlug(String name) {
        if (name == null || name.isBlank()) return "";
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    HealthcareAiCompanyEntity toEntity(HealthcareAiCompany company) {
        HealthcareAiCompanyEntity entity = new HealthcareAiCompanyEntity();
        entity.setCompanyId(company.companyId());
        entity.setSlug(computeSlug(company.name()));
        entity.setName(company.name());
        entity.setNameNormalized(company.nameNormalized());
        entity.setDomain(company.domain());
        entity.setDescription(company.description());
        entity.setHqLocation(company.hqLocation());
        entity.setFoundedYear(company.foundedYear());
        entity.setSector(company.sector());
        entity.setSubSector(company.subSector());
        entity.setFundingStage(company.fundingStage());
        entity.setEstimatedFunding(company.estimatedFunding());
        entity.setFoundersJson(company.foundersJson());
        entity.setSourceUrlsPipe(String.join("|", company.sourceUrls()));
        entity.setValidated(company.validated());
        entity.setValidationSourcesPipe(String.join("|", company.validationSources()));
        entity.setDiscoveredAt(company.discoveredAt());
        entity.setLastValidatedAt(company.lastValidatedAt());
        return entity;
    }

    HealthcareAiCompany toDomain(HealthcareAiCompanyEntity entity) {
        return new HealthcareAiCompany(
                entity.getCompanyId(),
                entity.getName(),
                entity.getNameNormalized(),
                cleanStr(entity.getDomain()),
                cleanStr(entity.getDescription()),
                cleanStr(entity.getHqLocation()),
                entity.getFoundedYear(),
                cleanStr(entity.getSector()),
                cleanStr(entity.getSubSector()),
                cleanStr(entity.getFundingStage()),
                cleanStr(entity.getEstimatedFunding()),
                cleanStr(entity.getFoundersJson()),
                parsePipe(entity.getSourceUrlsPipe()),
                entity.isValidated(),
                parsePipe(entity.getValidationSourcesPipe()),
                entity.getDiscoveredAt(),
                entity.getLastValidatedAt()
        );
    }

    /** Returns null when the value is Java null, blank, or the literal string "null". */
    private static String cleanStr(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value;
    }

    private List<String> parsePipe(String pipe) {
        if (pipe == null || pipe.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : pipe.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
