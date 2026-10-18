package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.LlmCallUsage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code llm_call_usage} table.
 *
 * <p>Maps to the immutable {@link LlmCallUsage} domain record.  Conversion
 * between entity and domain is performed via the {@link #toDomain()} instance
 * method and the {@link #fromDomain(LlmCallUsage)} static factory method.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@Entity
@Table(name = "llm_call_usage")
public class LlmCallUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pipeline_id", nullable = false, length = 50)
    private String pipelineId;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Required no-arg constructor for JPA. */
    public LlmCallUsageEntity() {}

    /**
     * Converts this entity to its immutable domain representation.
     *
     * @return the corresponding {@link LlmCallUsage} domain record
     */
    public LlmCallUsage toDomain() {
        return new LlmCallUsage(id, pipelineId, model, promptTokens, outputTokens, totalTokens, occurredAt);
    }

    /**
     * Creates a new entity from the given domain record.
     *
     * @param usage the domain record to convert
     * @return the corresponding JPA entity
     */
    public static LlmCallUsageEntity fromDomain(LlmCallUsage usage) {
        LlmCallUsageEntity entity = new LlmCallUsageEntity();
        entity.setId(usage.id());
        entity.setPipelineId(usage.pipelineId());
        entity.setModel(usage.model());
        entity.setPromptTokens(usage.promptTokens());
        entity.setOutputTokens(usage.outputTokens());
        entity.setTotalTokens(usage.totalTokens());
        entity.setOccurredAt(usage.occurredAt());
        return entity;
    }

    public Long getId()                              { return id; }
    public void setId(Long id)                       { this.id = id; }

    public String getPipelineId()                    { return pipelineId; }
    public void setPipelineId(String pipelineId)     { this.pipelineId = pipelineId; }

    public String getModel()                         { return model; }
    public void setModel(String model)               { this.model = model; }

    public int getPromptTokens()                     { return promptTokens; }
    public void setPromptTokens(int promptTokens)    { this.promptTokens = promptTokens; }

    public int getOutputTokens()                     { return outputTokens; }
    public void setOutputTokens(int outputTokens)    { this.outputTokens = outputTokens; }

    public int getTotalTokens()                      { return totalTokens; }
    public void setTotalTokens(int totalTokens)      { this.totalTokens = totalTokens; }

    public Instant getOccurredAt()                   { return occurredAt; }
    public void setOccurredAt(Instant occurredAt)    { this.occurredAt = occurredAt; }
}
