package com.wgblackmon.aihealthcare.web.dto;

import com.wgblackmon.aihealthcare.domain.model.DataJob;

import java.time.Instant;

/**
 * Response DTO for an enterprise data job. Never exposes internal file paths.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataJobResponse(
        String jobId,
        String feedId,
        String promptId,
        String format,
        String status,
        Integer rowCount,
        Long byteSize,
        String contentSha256,
        String errorType,
        String errorMessage,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt,
        Instant expiresAt
) {

    public static DataJobResponse from(DataJob job) {
        return new DataJobResponse(
                job.jobId(),
                job.feedId(),
                job.promptId(),
                job.format() != null ? job.format().name() : null,
                job.status().name(),
                job.rowCount(),
                job.byteSize(),
                job.contentSha256(),
                job.errorType(),
                job.errorMessage(),
                job.submittedAt(),
                job.startedAt(),
                job.completedAt(),
                job.expiresAt());
    }
}
