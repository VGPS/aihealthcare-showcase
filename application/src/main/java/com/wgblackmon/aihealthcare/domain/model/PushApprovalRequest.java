package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * A held enterprise push delivery awaiting human review before it is sent.
 *
 * <p>Created by {@code EnterpriseDataPushScheduler} in place of an immediate
 * {@code DataPushDeliveryPort.deliver(...)} call when the owning schedule's
 * approval gate is set. The underlying {@link DataJob} has already reached
 * {@code SUCCEEDED} and its artifact already exists — this record only gates
 * the delivery step, never the data generation itself.
 *
 * @param jobId       the completed job whose delivery is held
 * @param scheduleId  the push schedule that produced the job
 * @param ownerEmail  the schedule's owning customer (tenancy key)
 * @param requestedAt when the hold was created
 * @param status      current review status
 * @param decidedAt   when an admin decided; null while PENDING
 * @param decidedBy   which admin decided; null while PENDING
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public record PushApprovalRequest(
        String jobId,
        String scheduleId,
        String ownerEmail,
        Instant requestedAt,
        PushApprovalStatus status,
        Instant decidedAt,
        String decidedBy
) {
    public PushApprovalRequest {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId must not be blank");
        }
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new IllegalArgumentException("scheduleId must not be blank");
        }
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("ownerEmail must not be blank");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }
}
