package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link LintReportEntity}.
 *
 * <p>Supports retrieval of wiki lint reports for dashboard display
 * and trend analysis of wiki health over time.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
public interface LintReportRepository extends JpaRepository<LintReportEntity, Long> {

    /**
     * Finds all lint reports ordered by run start time descending
     * (most recent first).
     *
     * @return all reports, newest first
     */
    List<LintReportEntity> findAllByOrderByRunStartedAtDesc();

    /**
     * Finds the most recent lint report.
     *
     * @return the latest report, or {@code null} if none exist
     */
    LintReportEntity findFirstByOrderByRunStartedAtDesc();
}
