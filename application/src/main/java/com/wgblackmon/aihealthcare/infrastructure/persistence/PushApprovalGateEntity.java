package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity storing the approval-gate flag for one enterprise push schedule.
 *
 * <p>One row per {@code scheduleId}. Absence of a row means the gate is not
 * required — deliveries fire immediately, the ED-2 default behavior.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@Entity
@Table(name = "push_approval_gates")
public class PushApprovalGateEntity {

    @Id
    @Column(name = "schedule_id")
    private String scheduleId;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
