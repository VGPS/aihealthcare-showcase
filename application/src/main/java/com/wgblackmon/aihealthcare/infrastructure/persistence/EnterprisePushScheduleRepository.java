package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link EnterprisePushScheduleEntity}.
 *
 * <p>The {@code claim} query is the concurrency primitive for the push
 * sweeper — see {@link DataPushScheduleAdapter#claim} for full semantics.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface EnterprisePushScheduleRepository extends JpaRepository<EnterprisePushScheduleEntity, String> {

    Optional<EnterprisePushScheduleEntity> findByScheduleIdAndOwnerEmail(String scheduleId, String ownerEmail);

    List<EnterprisePushScheduleEntity> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);

    @Query("SELECT e FROM EnterprisePushScheduleEntity e " +
           "WHERE e.active = true AND e.nextRunAt IS NOT NULL AND e.nextRunAt <= :now " +
           "ORDER BY e.nextRunAt ASC")
    List<EnterprisePushScheduleEntity> findDue(Instant now, Pageable pageable);

    /**
     * Atomically claims a schedule by advancing {@code next_run_at} only if it
     * still equals the value the sweeper observed. Returns the number of rows
     * updated (0 or 1).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EnterprisePushScheduleEntity e " +
           "SET e.nextRunAt = :newNextRunAt, " +
           "    e.lastRunAt = :now, " +
           "    e.lastStatus = 'RUNNING', " +
           "    e.updatedAt = :now " +
           "WHERE e.scheduleId = :scheduleId " +
           "  AND e.active = true " +
           "  AND e.nextRunAt = :observedNextRunAt")
    int claim(String scheduleId, Instant observedNextRunAt, Instant newNextRunAt, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EnterprisePushScheduleEntity e " +
           "SET e.lastStatus = :status, " +
           "    e.lastJobId = :jobId, " +
           "    e.consecutiveFailures = CASE WHEN :status = 'SUCCEEDED' THEN 0 " +
           "                                 ELSE e.consecutiveFailures + 1 END, " +
           "    e.updatedAt = :completedAt " +
           "WHERE e.scheduleId = :scheduleId")
    int recordOutcome(String scheduleId, String jobId, String status, Instant completedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EnterprisePushScheduleEntity e " +
           "SET e.active = false, " +
           "    e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.scheduleId = :scheduleId")
    int deactivate(String scheduleId);

    void deleteByScheduleIdAndOwnerEmail(String scheduleId, String ownerEmail);

    int countByOwnerEmail(String ownerEmail);
}
