package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;
import com.wgblackmon.aihealthcare.domain.service.HealthcareAiCompanyClassifier;
import com.wgblackmon.aihealthcare.domain.service.SlugUtils;
import jakarta.annotation.PostConstruct;
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

    private static final int SLUG_MAX_LENGTH = 200;
    private static final int DEFAULT_MAX_LENGTH = 255;
    private static final int SECTOR_MAX_LENGTH = 100;
    private static final int SUB_SECTOR_MAX_LENGTH = 100;
    private static final int CATEGORY_MAX_LENGTH = 100;
    private static final int FUNDING_STAGE_MAX_LENGTH = 50;
    private static final int ESTIMATED_FUNDING_MAX_LENGTH = 100;

    private final HealthcareAiCompanyRepository repository;
    private final HealthcareAiCompanyClassifier classifier;

    public HealthcareAiCompanyAdapter(HealthcareAiCompanyRepository repository,
                                       HealthcareAiCompanyClassifier classifier) {
        log.debug("HealthcareAiCompanyAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
        this.classifier = classifier;
    }

    @PostConstruct
    public void backfillCategoriesOnStartup() {
        backfillCategories();
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

    @Override
    public void backfillCategories() {
        log.debug("backfillCategories() |");
        List<HealthcareAiCompanyEntity> all = repository.findAll();
        List<HealthcareAiCompanyEntity> toSave = new ArrayList<>();
        for (HealthcareAiCompanyEntity entity : all) {
            if (entity.getCategory() == null || entity.getCategory().isBlank()) {
                String category = classifier.classify(entity.getName(),
                        entity.getDescription(), entity.getSubSector());
                entity.setCategory(category);
                toSave.add(entity);
            }
        }
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
            log.info("backfillCategories() | backfilled {} companies", toSave.size());
        }
        log.debug("backfillCategories() | return=void");
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    /** Converts a display name to a URL-safe slug (e.g. "Grelin Health" → "grelin-health"). */
    static String computeSlug(String name) {
        return SlugUtils.toSlug(name);
    }

    HealthcareAiCompanyEntity toEntity(HealthcareAiCompany company) {
        HealthcareAiCompanyEntity entity = new HealthcareAiCompanyEntity();
        String category = company.category() != null ? company.category()
                : classifier.classify(company.name(), company.description(), company.subSector());

        entity.setCompanyId(company.companyId());
        entity.setSlug(truncate(company.companyId(), computeSlug(company.name()), "slug", SLUG_MAX_LENGTH));
        entity.setName(truncate(company.companyId(), company.name(), "name", DEFAULT_MAX_LENGTH));
        entity.setNameNormalized(truncate(company.companyId(), company.nameNormalized(), "nameNormalized", DEFAULT_MAX_LENGTH));
        entity.setDomain(truncate(company.companyId(), company.domain(), "domain", DEFAULT_MAX_LENGTH));
        entity.setDescription(company.description());
        entity.setHqLocation(truncate(company.companyId(), company.hqLocation(), "hqLocation", DEFAULT_MAX_LENGTH));
        entity.setFoundedYear(company.foundedYear());
        entity.setSector(truncate(company.companyId(), company.sector(), "sector", SECTOR_MAX_LENGTH));
        entity.setSubSector(truncate(company.companyId(), company.subSector(), "subSector", SUB_SECTOR_MAX_LENGTH));
        entity.setCategory(truncate(company.companyId(), category, "category", CATEGORY_MAX_LENGTH));
        entity.setFundingStage(truncate(company.companyId(), company.fundingStage(), "fundingStage", FUNDING_STAGE_MAX_LENGTH));
        entity.setEstimatedFunding(truncate(company.companyId(), company.estimatedFunding(), "estimatedFunding", ESTIMATED_FUNDING_MAX_LENGTH));
        entity.setFoundersJson(company.foundersJson());
        entity.setSourceUrlsPipe(String.join("|", company.sourceUrls()));
        entity.setValidated(company.validated());
        entity.setValidationSourcesPipe(String.join("|", company.validationSources()));
        entity.setDiscoveredAt(company.discoveredAt());
        entity.setLastValidatedAt(company.lastValidatedAt());
        return entity;
    }

    /**
     * Clips a value to the database column's max length, logging a warning
     * when clipping actually occurs.
     *
     * <p>These fields are extracted from freeform Perplexity LLM output —
     * "Series B" is expected in {@code fundingStage}, but a model that
     * ignores the schema hint can return a full descriptive sentence instead.
     * Truncating here keeps the company record instead of losing it to a
     * {@code DataIntegrityViolationException}.
     *
     * @param companyId the owning company's id, for the warning log
     * @param value     the value to clip; null passes through unchanged
     * @param fieldName the column name, for the warning log
     * @param maxLength the column's max length
     * @return the value, clipped to {@code maxLength} characters if needed
     */
    private String truncate(String companyId, String value, String fieldName, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.warn("truncate() | company {} field '{}' is {} chars, exceeding column limit of {} — clipping",
                companyId, fieldName, value.length(), maxLength);
        return value.substring(0, maxLength);
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
                cleanStr(entity.getCategory()),
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
