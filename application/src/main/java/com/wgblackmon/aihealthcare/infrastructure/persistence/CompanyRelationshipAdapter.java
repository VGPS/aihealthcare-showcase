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

    private final CompanyRelationshipRepository repository;

    public CompanyRelationshipAdapter(CompanyRelationshipRepository repository) {
        log.debug("CompanyRelationshipAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void saveAll(List<CompanyRelationship> relationships) {
        log.debug("saveAll() | relationships={}", relationships.size());
        List<CompanyRelationshipEntity> entities = new ArrayList<>();
        for (CompanyRelationship rel : relationships) {
            entities.add(toEntity(rel));
        }
        repository.saveAll(entities);
        log.debug("saveAll() | return=void");
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
        entity.setSourceCompany(rel.sourceCompany());
        entity.setTargetCompany(rel.targetCompany());
        entity.setRelationshipType(rel.relationshipType().name());
        entity.setEvidenceArticleId(rel.evidenceArticleId());
        entity.setSummary(rel.summary());
        entity.setConfidence(rel.confidence());
        entity.setDetectedAt(rel.detectedAt());
        return entity;
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
