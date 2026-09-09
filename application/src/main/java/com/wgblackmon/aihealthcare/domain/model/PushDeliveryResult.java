package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of a single push delivery attempt.
 *
 * <p>The delivery adapter never throws; it returns a failed result so the
 * scheduler can record the outcome and apply back-off without needing a
 * try/catch around every delivery call.
 *
 * @param scheduleId  the schedule that triggered this delivery
 * @param jobId       the job whose artifact was delivered
 * @param mode        ATTACHMENT or SIGNED_LINK
 * @param recipients  email addresses the delivery was sent to
 * @param byteSize    artifact size in bytes
 * @param deliveredAt when delivery was attempted
 * @param success     true if the email was accepted by the mail server
 * @param errorMessage null on success; the failure reason on failure
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record PushDeliveryResult(
        String scheduleId,
        String jobId,
        PushDeliveryMode mode,
        List<String> recipients,
        long byteSize,
        Instant deliveredAt,
        boolean success,
        String errorMessage
) {

    public PushDeliveryResult {
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new IllegalArgumentException("scheduleId must not be blank");
        }
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId must not be blank");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (recipients == null) {
            throw new IllegalArgumentException("recipients must not be null");
        }
        recipients = List.copyOf(recipients);
    }
}
