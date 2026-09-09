package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for the {@code enterprise_data_jobs} table — every PULL and PUSH
 * execution, whatever its origin.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Entity
@Table(name = "enterprise_data_jobs", indexes = {
        @Index(name = "idx_edj_owner_submitted", columnList = "owner_email, submitted_at DESC"),
        @Index(name = "idx_edj_status", columnList = "status"),
        @Index(name = "idx_edj_schedule", columnList = "schedule_id")
})
public class EnterpriseDataJobEntity {

    @Id
    @Column(name = "job_id", length = 36)
    private String jobId;

    @Column(name = "owner_email", nullable = false, length = 320)
    private String ownerEmail;

    @Column(name = "team_id", length = 36)
    private String teamId;

    @Column(name = "mode", nullable = false, length = 16)
    private String mode;

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

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "byte_size")
    private Long byteSize;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "artifact_path", length = 512)
    private String artifactPath;

    @Column(name = "log_path", length = 512)
    private String logPath;

    @Column(name = "error_type", length = 64)
    private String errorType;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "schedule_id", length = 36)
    private String scheduleId;

    /** Required no-arg constructor for JPA. */
    public EnterpriseDataJobEntity() {}

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }
    public String getContentSha256() { return contentSha256; }
    public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }
    public String getArtifactPath() { return artifactPath; }
    public void setArtifactPath(String artifactPath) { this.artifactPath = artifactPath; }
    public String getLogPath() { return logPath; }
    public void setLogPath(String logPath) { this.logPath = logPath; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(Instant heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }
}
