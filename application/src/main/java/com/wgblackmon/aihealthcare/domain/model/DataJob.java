package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable snapshot of an enterprise data job's state.
 *
 * <p>Every PULL and PUSH execution is recorded as a {@code DataJob}. The
 * record is persisted by {@link com.wgblackmon.aihealthcare.domain.port.outbound.DataJobPort}
 * and exposed through
 * {@link com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase}.
 *
 * @param jobId          UUID primary key
 * @param ownerEmail     tenancy key — every lookup includes this
 * @param teamId         optional team scope (null in ED-1)
 * @param mode           PULL or PUSH
 * @param feedId         which feed was queried
 * @param promptId       canned prompt used (nullable)
 * @param format         export format
 * @param status         lifecycle state
 * @param rowCount       rows in the artifact (null until SUCCEEDED)
 * @param byteSize       artifact bytes (null until SUCCEEDED)
 * @param contentSha256  hex SHA-256 of the artifact (null until SUCCEEDED)
 * @param artifactPath   server-side artifact filename (null until SUCCEEDED)
 * @param logPath        server-side log filename
 * @param errorType      failure classification (null unless FAILED)
 * @param errorMessage   failure detail (null unless FAILED)
 * @param submittedAt    when the job was queued
 * @param startedAt      when execution began (null until RUNNING)
 * @param completedAt    when execution ended (null until terminal)
 * @param heartbeatAt    last heartbeat from the runner (null until RUNNING)
 * @param expiresAt      when retention sweeper may delete artifacts
 * @param scheduleId     owning push schedule (null for PULL jobs)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataJob(
        String jobId,
        String ownerEmail,
        String teamId,
        DataJobMode mode,
        String feedId,
        String promptId,
        ExportFormat format,
        DataJobStatus status,
        Integer rowCount,
        Long byteSize,
        String contentSha256,
        String artifactPath,
        String logPath,
        String errorType,
        String errorMessage,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt,
        Instant heartbeatAt,
        Instant expiresAt,
        String scheduleId
) {
}
