package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link IntelReportEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public interface IntelReportRepository extends JpaRepository<IntelReportEntity, String> {

    /**
     * Retrieves all reports ordered by generation date descending (most recent first).
     *
     * @return list of report entities
     */
    List<IntelReportEntity> findAllByOrderByGeneratedAtDesc();
}
