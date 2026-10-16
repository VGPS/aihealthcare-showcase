package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity storing the pause state of one kill-switch-gated pipeline.
 *
 * <p>One row per {@code pipelineKey} (e.g. {@code "digest-send"},
 * {@code "enterprise-push"}). Absence of a row means not paused.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-16
 * @updated 2026-10-16
 */
@Entity
@Table(name = "pipeline_pauses")
public class PipelinePauseEntity {

    @Id
    @Column(name = "pipeline_key")
    private String pipelineKey;

    @Column(name = "paused", nullable = false)
    private boolean paused;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getPipelineKey() {
        return pipelineKey;
    }

    public void setPipelineKey(String pipelineKey) {
        this.pipelineKey = pipelineKey;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
