package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a single pipeline step execution event.
 *
 * <p>Captures the outcome of one step within a pipeline run, including timing,
 * status, item counts, and any error message.  Used by the admin pipeline
 * dashboard to display run history and health status per pipeline.
 *
 * <p>The {@code triggerSource} field identifies what initiated the pipeline run:
 * {@code "SCHEDULER"} for cron-triggered runs, {@code "ORCHESTRATOR"} for
 * cascade runs initiated by {@code StartupPipelineOrchestrator}, and
 * {@code "MANUAL"} for admin-triggered runs via the UI or REST API.
 *
 * @param id              database-generated surrogate key (null before persistence)
 * @param pipelineId      short identifier for the pipeline (e.g. "rss-harvest", "wiki-compile")
 * @param stepName        human-readable step name within the pipeline
 * @param status          outcome of the step execution
 * @param startedAt       when the step began executing
 * @param completedAt     when the step finished executing
 * @param durationMs      wall-clock duration in milliseconds
 * @param errorMessage    error detail if status is FAILED; null otherwise
 * @param itemsProcessed  count of items handled during this step (0 if not applicable)
 * @param triggerSource   what initiated this run: SCHEDULER, ORCHESTRATOR, or MANUAL
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public record PipelineRunEvent(
        Long id,
        String pipelineId,
        String stepName,
        PipelineStepStatus status,
        Instant startedAt,
        Instant completedAt,
        long durationMs,
        String errorMessage,
        int itemsProcessed,
        String triggerSource
) {

    /**
     * Compact constructor — validates required fields.
     */
    public PipelineRunEvent {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId must not be null or blank");
        }
        if (stepName == null || stepName.isBlank()) {
            throw new IllegalArgumentException("stepName must not be null or blank");
        }
    }
}
