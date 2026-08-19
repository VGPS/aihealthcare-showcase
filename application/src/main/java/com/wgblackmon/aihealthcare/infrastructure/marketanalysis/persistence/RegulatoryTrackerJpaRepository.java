package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RegulatoryTrackerEntity}.
 *
 * <p>The {@code findByDocketIdAndJurisdiction} method is used to check for an existing
 * row before upserting. The approaching-deadlines query returns trackers with a non-null
 * comment deadline on or before the given date.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface RegulatoryTrackerJpaRepository
        extends JpaRepository<RegulatoryTrackerEntity, Long> {

    Optional<RegulatoryTrackerEntity> findByDocketIdAndJurisdiction(
            String docketId, String jurisdiction);

    List<RegulatoryTrackerEntity> findByCommentDeadlineLessThanEqualOrderByCommentDeadlineAsc(
            LocalDate deadlineOnOrBefore);

    List<RegulatoryTrackerEntity> findAllByOrderByLastUpdatedAtDesc();
}
