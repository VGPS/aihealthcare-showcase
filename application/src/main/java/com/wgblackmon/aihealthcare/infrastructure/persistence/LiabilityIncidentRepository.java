package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link LiabilityIncidentEntity}.
 *
 * <p>The default sort (filing date descending, nulls last) is handled in
 * {@link LiabilityIncidentAdapter} with an {@code ORDER BY} JPQL query rather
 * than a derived method name, to keep the null-last behaviour explicit.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-16
 * @updated 2026-11-16
 */
public interface LiabilityIncidentRepository extends JpaRepository<LiabilityIncidentEntity, String> {

    /**
     * Returns all incidents ordered by filing date descending, nulls last,
     * then by created date descending as a tiebreaker.
     */
    List<LiabilityIncidentEntity> findAllByOrderByFilingDateDescCreatedAtDesc();
}
