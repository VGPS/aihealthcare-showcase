package com.wgblackmon.aihealthcare.domain.port.outbound;

/**
 * Outbound port for the per-schedule human-approval gate.
 *
 * <p>Deliberately separate from {@link com.wgblackmon.aihealthcare.domain.model.DataPushSchedule}
 * so flagging a schedule for review never touches the ED-2 claim-guard record or its
 * at-most-once delivery invariants — this is purely an admin-side flag the scheduler
 * consults after a job has already succeeded, before handing it to
 * {@link DataPushDeliveryPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public interface PushApprovalGatePort {

    /**
     * Returns true if deliveries for the given schedule must be held for admin review.
     *
     * @param scheduleId the schedule to check
     * @return true if a human approval checkpoint is required before delivery
     */
    boolean isRequired(String scheduleId);

    /**
     * Sets or clears the approval gate for the given schedule.
     *
     * @param scheduleId stable identifier for the gated schedule
     * @param required   true to require approval before delivery, false to deliver immediately
     */
    void setRequired(String scheduleId, boolean required);
}
