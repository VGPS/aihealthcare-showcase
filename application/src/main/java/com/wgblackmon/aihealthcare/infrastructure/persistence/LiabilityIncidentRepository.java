package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link LiabilityIncidentEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-11-16
 * @updated 2026-11-15
 */
public interface LiabilityIncidentRepository extends JpaRepository<LiabilityIncidentEntity, String> {

    List<LiabilityIncidentEntity> findAllByOrderByFilingDateDescCreatedAtDesc();

    List<LiabilityIncidentEntity> findAllByStatusOrderByCreatedAtDesc(String status);

    boolean existsBySourceUrl(String sourceUrl);
}
