package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.PipelineErrorType;
import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;
import com.wgblackmon.aihealthcare.domain.model.PipelineStepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code pipeline_run_events} table.
 *
 * <p>Maps to the immutable {@link PipelineRunEvent} domain record.  Conversion
 * between entity and domain is performed via the {@link #toDomain()} instance
 * method and the {@link #fromDomain(PipelineRunEvent)} static factory method.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-04
 * @updated 2026-08-23
 */
@Entity
@Table(name = "pipeline_run_events")
public class PipelineRunEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pipeline_id", nullable = false, length = 50)
    private String pipelineId;

    @Column(name = "step_name", nullable = false, length = 100)
    private String stepName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "items_processed", nullable = false)
    private int itemsProcessed;

    @Column(name = "trigger_source", nullable = false, length = 20)
    private String triggerSource;

    @Column(name = "error_type", length = 20)
    private String errorType;

    @Column(name = "error_provider", length = 50)
    private String errorProvider;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    /** Required no-arg constructor for JPA. */
    public PipelineRunEventEntity() {}

    /**
     * Converts this entity to its immutable domain representation.
     *
     * @return the corresponding {@link PipelineRunEvent} domain record
     */
    public PipelineRunEvent toDomain() {
        return new PipelineRunEvent(
                id,
                pipelineId,
                stepName,
                PipelineStepStatus.valueOf(status),
                startedAt,
                completedAt,
                durationMs,
                errorMessage,
                itemsProcessed,
                triggerSource,
                errorType != null ? PipelineErrorType.valueOf(errorType) : null,
                errorProvider,
                errorDetail
        );
    }

    /**
     * Creates a new entity from the given domain record.
     *
     * @param event the domain record to convert
     * @return the corresponding JPA entity
     */
    public static PipelineRunEventEntity fromDomain(PipelineRunEvent event) {
        PipelineRunEventEntity entity = new PipelineRunEventEntity();
        entity.setId(event.id());
        entity.setPipelineId(event.pipelineId());
        entity.setStepName(event.stepName());
        entity.setStatus(event.status().name());
        entity.setStartedAt(event.startedAt());
        entity.setCompletedAt(event.completedAt());
        entity.setDurationMs(event.durationMs());
        entity.setErrorMessage(event.errorMessage());
        entity.setItemsProcessed(event.itemsProcessed());
        entity.setTriggerSource(event.triggerSource());
        entity.setErrorType(event.errorType() != null ? event.errorType().name() : null);
        entity.setErrorProvider(event.errorProvider());
        entity.setErrorDetail(event.errorDetail());
        return entity;
    }

    public Long getId()                                 { return id; }
    public void setId(Long id)                          { this.id = id; }

    public String getPipelineId()                       { return pipelineId; }
    public void setPipelineId(String pipelineId)        { this.pipelineId = pipelineId; }

    public String getStepName()                         { return stepName; }
    public void setStepName(String stepName)            { this.stepName = stepName; }

    public String getStatus()                           { return status; }
    public void setStatus(String status)                { this.status = status; }

    public Instant getStartedAt()                       { return startedAt; }
    public void setStartedAt(Instant startedAt)         { this.startedAt = startedAt; }

    public Instant getCompletedAt()                     { return completedAt; }
    public void setCompletedAt(Instant completedAt)     { this.completedAt = completedAt; }

    public long getDurationMs()                         { return durationMs; }
    public void setDurationMs(long durationMs)          { this.durationMs = durationMs; }

    public String getErrorMessage()                     { return errorMessage; }
    public void setErrorMessage(String errorMessage)    { this.errorMessage = errorMessage; }

    public int getItemsProcessed()                      { return itemsProcessed; }
    public void setItemsProcessed(int itemsProcessed)   { this.itemsProcessed = itemsProcessed; }

    public String getTriggerSource()                    { return triggerSource; }
    public void setTriggerSource(String triggerSource)   { this.triggerSource = triggerSource; }

    public String getErrorType()                        { return errorType; }
    public void setErrorType(String errorType)          { this.errorType = errorType; }

    public String getErrorProvider()                    { return errorProvider; }
    public void setErrorProvider(String errorProvider)  { this.errorProvider = errorProvider; }

    public String getErrorDetail()                      { return errorDetail; }
    public void setErrorDetail(String errorDetail)      { this.errorDetail = errorDetail; }
}
