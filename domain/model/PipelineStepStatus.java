package com.wgblackmon.aihealthcare.domain.model;

/**
 * Outcome status of a single pipeline step execution.
 *
 * <p>Each step in the {@code StartupPipelineOrchestrator} cascade completes
 * with one of these statuses.  {@code SUCCESS} means the step ran without
 * errors, {@code FAILED} means an exception was caught and logged, and
 * {@code SKIPPED} means the step was intentionally bypassed (e.g. because
 * a prerequisite was not met or the step was disabled by configuration).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public enum PipelineStepStatus {

    /** The pipeline step completed without errors. */
    SUCCESS,

    /** The pipeline step threw an exception and did not complete. */
    FAILED,

    /** The pipeline step was intentionally bypassed. */
    SKIPPED
}
