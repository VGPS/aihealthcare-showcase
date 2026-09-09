package com.wgblackmon.aihealthcare.web.dto;

import com.wgblackmon.aihealthcare.domain.model.DataPushSchedule;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for a push schedule. Includes optional {@code nextRuns}
 * preview on create/update.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataPushScheduleResponse(
        String scheduleId,
        String label,
        String feedId,
        String promptId,
        String format,
        String cronExpression,
        String zoneId,
        List<String> recipients,
        boolean active,
        Instant nextRunAt,
        Instant lastRunAt,
        String lastStatus,
        String lastJobId,
        int consecutiveFailures,
        Instant createdAt,
        Instant updatedAt,
        List<Instant> nextRuns
) {

    public static DataPushScheduleResponse from(DataPushSchedule s) {
        return from(s, null);
    }

    public static DataPushScheduleResponse from(DataPushSchedule s, List<Instant> nextRuns) {
        return new DataPushScheduleResponse(
                s.scheduleId(),
                s.label(),
                s.feedId(),
                s.promptId(),
                s.format() != null ? s.format().name() : null,
                s.cronExpression(),
                s.zoneId(),
                s.recipients(),
                s.active(),
                s.nextRunAt(),
                s.lastRunAt(),
                s.lastStatus() != null ? s.lastStatus().name() : null,
                s.lastJobId(),
                s.consecutiveFailures(),
                s.createdAt(),
                s.updatedAt(),
                nextRuns);
    }
}
