package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for the {@code enterprise_data_audit} table — append-only
 * compliance trail for enterprise data access.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Entity
@Table(name = "enterprise_data_audit", indexes = {
        @Index(name = "idx_eda_owner_time", columnList = "owner_email, occurred_at DESC")
})
public class EnterpriseDataAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "owner_email", nullable = false, length = 320)
    private String ownerEmail;

    @Column(name = "job_id", length = 36)
    private String jobId;

    @Column(name = "schedule_id", length = 36)
    private String scheduleId;

    @Column(name = "action", nullable = false, length = 48)
    private String action;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "detail", length = 1024)
    private String detail;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "byte_size")
    private Long byteSize;

    /** Required no-arg constructor for JPA. */
    public EnterpriseDataAuditEntity() {}

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }
}
