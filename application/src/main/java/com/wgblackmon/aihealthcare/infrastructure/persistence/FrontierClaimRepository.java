package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link FrontierClaimEntity}.
 *
 * <p>Provides derived queries for company (case-insensitive), verdict, and
 * claim type filtering. Results are ordered by {@code detectedAt} descending
 * so the most recently discovered claims appear first.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
public interface FrontierClaimRepository extends JpaRepository<FrontierClaimEntity, String> {

    List<FrontierClaimEntity> findByCompanyIgnoreCaseOrderByDetectedAtDesc(String company);

    List<FrontierClaimEntity> findByVerdictOrderByDetectedAtDesc(String verdict);

    List<FrontierClaimEntity> findByClaimTypeOrderByDetectedAtDesc(String claimType);

    List<FrontierClaimEntity> findAllByOrderByDetectedAtDesc();

    List<FrontierClaimEntity> findByCompanyIgnoreCaseAndDetectedAtAfterOrderByDetectedAtDesc(
            String company, Instant since);
}
