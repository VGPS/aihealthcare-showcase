package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link CompilationReportEntity}.
 *
 * <p>Supports retrieval of compilation run history for dashboard display
 * and audit trail purposes.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public interface CompilationReportRepository extends JpaRepository<CompilationReportEntity, Long> {

    /**
     * Finds all compilation reports ordered by run start time descending
     * (most recent first).
     *
     * @return all reports, newest first
     */
    List<CompilationReportEntity> findAllByOrderByRunStartedAtDesc();
}
