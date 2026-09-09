package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for the {@code enterprise_push_schedules} table — recurring
 * push delivery configurations owned by ENTERPRISE customers.
 *
 * <p>The {@code idx_eps_due} index on {@code (active, next_run_at)} is
 * critical for the sweeper's per-minute query performance.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Entity
@Table(name = "enterprise_push_schedules", indexes = {
        @Index(name = "idx_eps_owner", columnList = "owner_email"),
        @Index(name = "idx_eps_due", columnList = "active, next_run_at")
})
public class EnterprisePushScheduleEntity {

    @Id
    @Column(name = "schedule_id", length = 36)
    private String scheduleId;

    @Column(name = "owner_email", nullable = false, length = 320)
    private String ownerEmail;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Column(name = "feed_id", nullable = false, length = 64)
    private String feedId;

    @Column(name = "prompt_id", length = 64)
    private String promptId;

    @Column(name = "prompt_text", columnDefinition = "TEXT")
    private String promptText;

    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "format", nullable = false, length = 16)
    private String format;

    @Column(name = "cron_expression", nullable = false, length = 64)
    private String cronExpression;

    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId;

    @Column(name = "recipients", nullable = false, columnDefinition = "TEXT")
    private String recipients;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_status", length = 16)
    private String lastStatus;

    @Column(name = "last_job_id", length = 36)
    private String lastJobId;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required no-arg constructor for JPA. */
    public EnterprisePushScheduleEntity() {}

    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getFeedId() { return feedId; }
    public void setFeedId(String feedId) { this.feedId = feedId; }
    public String getPromptId() { return promptId; }
    public void setPromptId(String promptId) { this.promptId = promptId; }
    public String getPromptText() { return promptText; }
    public void setPromptText(String promptText) { this.promptText = promptText; }
    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public String getRecipients() { return recipients; }
    public void setRecipients(String recipients) { this.recipients = recipients; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
    public Instant getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
    public String getLastJobId() { return lastJobId; }
    public void setLastJobId(String lastJobId) { this.lastJobId = lastJobId; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
