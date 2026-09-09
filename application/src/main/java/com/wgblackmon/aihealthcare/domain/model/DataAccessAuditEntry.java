package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * A single row in the enterprise data audit trail.
 *
 * <p>Append-only: once written, audit entries are never modified or deleted.
 * They outlive the artifacts they describe, providing a durable compliance
 * record of who accessed what data, when, and whether it was allowed.
 *
 * @param occurredAt when the action happened
 * @param ownerEmail the account that triggered the action
 * @param jobId      the associated job, if any
 * @param scheduleId the associated push schedule, if any (ED-2)
 * @param action     what happened
 * @param outcome    ALLOW, DENY, SUCCESS, or FAILURE
 * @param detail     human-readable context (sanitised before storage)
 * @param rowCount   rows involved, if applicable
 * @param byteSize   bytes involved, if applicable
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataAccessAuditEntry(
        Instant occurredAt,
        String ownerEmail,
        String jobId,
        String scheduleId,
        DataAccessAction action,
        String outcome,
        String detail,
        Integer rowCount,
        Long byteSize
) {
}
