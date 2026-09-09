package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link EnterpriseDataJobEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08 — ED-2 findByScheduleId, countPushRunsSince
 */
public interface EnterpriseDataJobRepository extends JpaRepository<EnterpriseDataJobEntity, String> {

    Optional<EnterpriseDataJobEntity> findByJobIdAndOwnerEmail(String jobId, String ownerEmail);

    List<EnterpriseDataJobEntity> findByScheduleIdOrderBySubmittedAtDesc(String scheduleId, Pageable pageable);

    Page<EnterpriseDataJobEntity> findByOwnerEmailOrderBySubmittedAtDesc(String ownerEmail, Pageable pageable);

    long countByOwnerEmailAndStatusIn(String ownerEmail, List<String> statuses);

    long countByOwnerEmailAndModeAndSubmittedAtAfter(String ownerEmail, String mode, Instant since);

    List<EnterpriseDataJobEntity> findByStatusAndHeartbeatAtBefore(String status, Instant cutoff);

    List<EnterpriseDataJobEntity> findByStatusInAndExpiresAtBefore(List<String> statuses, Instant now);

    @Modifying
    @Query("UPDATE EnterpriseDataJobEntity e SET e.status = :status WHERE e.jobId = :jobId")
    int updateStatusByJobId(String jobId, String status);

    @Modifying
    @Query("UPDATE EnterpriseDataJobEntity e SET e.heartbeatAt = :now WHERE e.jobId = :jobId")
    int touchHeartbeat(String jobId, Instant now);
}
