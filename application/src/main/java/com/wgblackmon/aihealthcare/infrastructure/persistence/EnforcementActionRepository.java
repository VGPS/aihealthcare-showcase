package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link EnforcementActionEntity}.
 *
 * @author  Bill Blackmon
 * @since   2026-08-16
 * @updated 2026-08-16
 */
public interface EnforcementActionRepository extends JpaRepository<EnforcementActionEntity, String> {

    List<EnforcementActionEntity> findByHealthSystemId(String healthSystemId);

    List<EnforcementActionEntity> findByHealthSystemIdOrderByAmountUsdDesc(String healthSystemId);

    List<EnforcementActionEntity> findByAgency(String agency);

    List<EnforcementActionEntity> findAllByOrderByAmountUsdDesc();
}
