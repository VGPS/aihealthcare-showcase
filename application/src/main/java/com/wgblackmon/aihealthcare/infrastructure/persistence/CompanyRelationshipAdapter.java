package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.CompanyRelationshipType;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyRelationshipPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link CompanyRelationshipPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class CompanyRelationshipAdapter implements CompanyRelationshipPort {

    private static final int COMPANY_NAME_MAX_LENGTH = 500;
    private static final int EVIDENCE_ARTICLE_ID_MAX_LENGTH = 500;

    private final CompanyRelationshipRepository repository;

    public CompanyRelationshipAdapter(CompanyRelationshipRepository repository) {
        log.debug("CompanyRelationshipAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void saveAll(List<CompanyRelationship> relationships) {
        log.debug("saveAll() | relationships={}", relationships.size());
        int saved = 0;
        for (CompanyRelationship rel : relationships) {
            try {
                repository.save(toEntity(rel));
                saved++;
            } catch (RuntimeException e) {
                log.warn("saveAll() | failed to persist relationship {} — skipping. cause={}",
                        rel.relationshipId(), e.getMessage());
            }
        }
        log.debug("saveAll() | return=void, saved={}/{}", saved, relationships.size());
    }

    @Override
    public List<CompanyRelationship> findAll() {
        log.debug("findAll()");
        List<CompanyRelationshipEntity> entities = repository.findAllByOrderByDetectedAtDesc();
        List<CompanyRelationship> result = new ArrayList<>();
        for (CompanyRelationshipEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findAll() | return={} relationships", result.size());
        return result;
    }

    @Override
    public List<CompanyRelationship> findByCompany(String companyName) {
        log.debug("findByCompany() | companyName={}", companyName);
        List<CompanyRelationshipEntity> entities =
                repository.findBySourceCompanyIgnoreCaseOrTargetCompanyIgnoreCase(
                        companyName, companyName);
        List<CompanyRelationship> result = new ArrayList<>();
        for (CompanyRelationshipEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findByCompany() | return={} relationships", result.size());
        return result;
    }

    @Override
    public boolean existsBySourceAndTargetAndType(String source, String target,
                                                   String relationshipType) {
        log.debug("existsBySourceAndTargetAndType() | source={}, target={}, type={}",
                source, target, relationshipType);
        boolean result = repository
                .existsBySourceCompanyIgnoreCaseAndTargetCompanyIgnoreCaseAndRelationshipType(
                        source, target, relationshipType);
        log.debug("existsBySourceAndTargetAndType() | return={}", result);
        return result;
    }

    private CompanyRelationshipEntity toEntity(CompanyRelationship rel) {
        CompanyRelationshipEntity entity = new CompanyRelationshipEntity();
        entity.setRelationshipId(rel.relationshipId());
        entity.setSourceCompany(truncate(rel.relationshipId(), rel.sourceCompany(), "sourceCompany", COMPANY_NAME_MAX_LENGTH));
        entity.setTargetCompany(truncate(rel.relationshipId(), rel.targetCompany(), "targetCompany", COMPANY_NAME_MAX_LENGTH));
        entity.setRelationshipType(rel.relationshipType().name());
        entity.setEvidenceArticleId(truncate(rel.relationshipId(), rel.evidenceArticleId(), "evidenceArticleId", EVIDENCE_ARTICLE_ID_MAX_LENGTH));
        entity.setSummary(rel.summary());
        entity.setConfidence(rel.confidence());
        entity.setDetectedAt(rel.detectedAt());
        return entity;
    }

    /**
     * Clips a value to the database column's max length, logging a warning
     * when clipping actually occurs. Company names and article ids here are
     * LLM-extracted or sourced from RSS entry URIs with no length guarantee.
     *
     * @param relationshipId the owning relationship's id, for the warning log
     * @param value          the value to clip; null passes through unchanged
     * @param fieldName      the column name, for the warning log
     * @param maxLength      the column's max length
     * @return the value, clipped to {@code maxLength} characters if needed
     */
    private String truncate(String relationshipId, String value, String fieldName, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.warn("truncate() | relationship {} field '{}' is {} chars, exceeding column limit of {} — clipping",
                relationshipId, fieldName, value.length(), maxLength);
        return value.substring(0, maxLength);
    }

    private CompanyRelationship toDomain(CompanyRelationshipEntity entity) {
        return new CompanyRelationship(
                entity.getRelationshipId(),
                entity.getSourceCompany(),
                entity.getTargetCompany(),
                CompanyRelationshipType.valueOf(entity.getRelationshipType()),
                entity.getEvidenceArticleId(),
                entity.getSummary(),
                entity.getConfidence(),
                entity.getDetectedAt()
        );
    }
}
