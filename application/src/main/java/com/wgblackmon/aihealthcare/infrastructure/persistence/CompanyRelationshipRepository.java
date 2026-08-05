package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link CompanyRelationshipEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface CompanyRelationshipRepository
        extends JpaRepository<CompanyRelationshipEntity, String> {

    List<CompanyRelationshipEntity> findAllByOrderByDetectedAtDesc();

    List<CompanyRelationshipEntity> findBySourceCompanyIgnoreCaseOrTargetCompanyIgnoreCase(
            String source, String target);

    boolean existsBySourceCompanyIgnoreCaseAndTargetCompanyIgnoreCaseAndRelationshipType(
            String source, String target, String relationshipType);
}
