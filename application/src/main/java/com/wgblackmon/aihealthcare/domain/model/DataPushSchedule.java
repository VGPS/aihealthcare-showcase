package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A scheduled push delivery configuration for an ENTERPRISE customer.
 *
 * <p>Represents a recurring job that fires at the times described by
 * {@code cronExpression} in the customer's {@code zoneId}, executes a
 * data pull using the configured feed/prompt, and delivers the result
 * by email to {@code recipients}.
 *
 * <p>The cron expression is <em>not</em> validated in this record because
 * {@code CronExpression} is a Spring class and the domain layer is
 * JDK-only. Validation happens in the application/infrastructure layer
 * (see {@code CronScheduleCalculator}).
 *
 * @param scheduleId           UUID primary key
 * @param ownerEmail           tenancy key — every lookup includes this
 * @param label                human-readable name for the schedule
 * @param feedId               which data feed to query
 * @param promptId             canned prompt id (nullable — free-text if null)
 * @param promptText           free-text prompt (nullable — canned if null)
 * @param parameters           additional query parameters
 * @param format               export format (CSV, JSON, PDF)
 * @param cronExpression       Spring 6-field cron expression
 * @param zoneId               customer's time zone (e.g. "America/Chicago")
 * @param recipients           email addresses to deliver to (at least one)
 * @param active               whether the schedule fires
 * @param nextRunAt            next computed fire time in UTC (nullable until first computed)
 * @param lastRunAt            last fire time (nullable)
 * @param lastStatus           status of the last job (nullable)
 * @param lastJobId            id of the last job (nullable)
 * @param consecutiveFailures  number of consecutive failed runs (0-based)
 * @param createdAt            when the schedule was created
 * @param updatedAt            when the schedule was last modified
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataPushSchedule(
        String scheduleId,
        String ownerEmail,
        String label,
        String feedId,
        String promptId,
        String promptText,
        Map<String, String> parameters,
        ExportFormat format,
        String cronExpression,
        String zoneId,
        List<String> recipients,
        boolean active,
        Instant nextRunAt,
        Instant lastRunAt,
        DataJobStatus lastStatus,
        String lastJobId,
        int consecutiveFailures,
        Instant createdAt,
        Instant updatedAt
) {

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public DataPushSchedule {
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new IllegalArgumentException("scheduleId must not be blank");
        }
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("ownerEmail must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (feedId == null || feedId.isBlank()) {
            throw new IllegalArgumentException("feedId must not be blank");
        }
        if (cronExpression == null || cronExpression.isBlank()) {
            throw new IllegalArgumentException("cronExpression must not be blank");
        }
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("zoneId must not be blank");
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        if (recipients == null || recipients.isEmpty()) {
            throw new IllegalArgumentException("recipients must contain at least one address");
        }
        for (String r : recipients) {
            if (r == null || !EMAIL_PATTERN.matcher(r).matches()) {
                throw new IllegalArgumentException("invalid recipient email: " + r);
            }
        }
        if (consecutiveFailures < 0) {
            throw new IllegalArgumentException("consecutiveFailures must be >= 0");
        }
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        recipients = List.copyOf(recipients);
    }
}
