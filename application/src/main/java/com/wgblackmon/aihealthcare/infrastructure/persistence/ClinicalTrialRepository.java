package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ClinicalTrialEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
public interface ClinicalTrialRepository extends JpaRepository<ClinicalTrialEntity, String> {

    boolean existsByNctId(String nctId);

    List<ClinicalTrialEntity> findAllByOrderByDiscoveredAtDesc();

    List<ClinicalTrialEntity> findByStatusOrderByDiscoveredAtDesc(String status);

    List<ClinicalTrialEntity> findByPhaseOrderByDiscoveredAtDesc(String phase);

    @Query("SELECT t FROM ClinicalTrialEntity t WHERE " +
           "LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(t.briefSummary) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(t.sponsor) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(t.conditions) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY t.discoveredAt DESC")
    List<ClinicalTrialEntity> findByKeyword(@Param("keyword") String keyword);
}
