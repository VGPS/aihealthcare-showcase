package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

/**
 * Spring Data JPA repository for {@link EnterpriseDataAuditEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface EnterpriseDataAuditRepository extends JpaRepository<EnterpriseDataAuditEntity, Long> {

    Page<EnterpriseDataAuditEntity> findByOwnerEmailOrderByOccurredAtDesc(String ownerEmail, Pageable pageable);

    Page<EnterpriseDataAuditEntity> findByOwnerEmailAndOccurredAtAfterOrderByOccurredAtDesc(
            String ownerEmail, Instant occurredAfter, Pageable pageable);
}
