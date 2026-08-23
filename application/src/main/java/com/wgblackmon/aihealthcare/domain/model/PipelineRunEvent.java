package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a single pipeline step execution event.
 *
 * <p>Captures the outcome of one step within a pipeline run, including timing,
 * status, item counts, and any error detail.  Used by the admin pipeline
 * dashboard to display run history and health status per pipeline.
 *
 * <p>The {@code triggerSource} field identifies what initiated the pipeline run:
 * {@code "SCHEDULER"} for cron-triggered runs, {@code "ORCHESTRATOR"} for
 * cascade runs initiated by {@code StartupPipelineOrchestrator}, and
 * {@code "MANUAL"} for admin-triggered runs via the UI or REST API.
 *
 * <p>The {@code errorType} field classifies FAILED runs to aid triage:
 * NETWORK = retryable; LLM_QUOTA = needs funding; LLM_AUTH = invalid key.
 * The {@code errorProvider} field names the specific LLM provider involved
 * when the failure originated from an AI adapter (e.g. "Anthropic", "OpenAI").
 * The {@code errorDetail} field holds the first 500 chars of the stack trace
 * for deeper debugging.
 *
 * @param id              database-generated surrogate key (null before persistence)
 * @param pipelineId      short identifier for the pipeline (e.g. "rss-harvest", "wiki-compile")
 * @param stepName        human-readable step name within the pipeline
 * @param status          outcome of the step execution
 * @param startedAt       when the step began executing
 * @param completedAt     when the step finished executing
 * @param durationMs      wall-clock duration in milliseconds
 * @param errorMessage    short error message if status is FAILED; null otherwise
 * @param itemsProcessed  count of items handled during this step (0 if not applicable)
 * @param triggerSource   what initiated this run: SCHEDULER, ORCHESTRATOR, or MANUAL
 * @param errorType       failure classification (null when status is SUCCESS/SKIPPED)
 * @param errorProvider   name of the LLM provider that threw the error (null if not LLM-related)
 * @param errorDetail     first 500 chars of the stack trace (null when status is SUCCESS/SKIPPED)
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-04
 * @updated 2026-08-23
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
        String triggerSource,
        PipelineErrorType errorType,
        String errorProvider,
        String errorDetail
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
