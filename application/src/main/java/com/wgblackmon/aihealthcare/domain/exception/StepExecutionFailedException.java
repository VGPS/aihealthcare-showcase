package com.wgblackmon.aihealthcare.domain.exception;

import com.wgblackmon.aihealthcare.domain.model.PipelineErrorType;

/**
 * Thrown by {@link com.wgblackmon.aihealthcare.domain.service.ResilientStepExecutor}
 * when a pipeline step cannot complete successfully — either its failure was
 * classified as non-retryable (e.g. {@link PipelineErrorType#LLM_AUTH}) or
 * retries were exhausted without a passing attempt.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-16
 * @updated 2026-10-16
 */
public class StepExecutionFailedException extends RuntimeException {

    private final String stepName;
    private final PipelineErrorType errorType;

    public StepExecutionFailedException(String stepName, PipelineErrorType errorType, Throwable cause) {
        super("Step '" + stepName + "' failed with non-retryable error " + errorType, cause);
        this.stepName = stepName;
        this.errorType = errorType;
    }

    public StepExecutionFailedException(String stepName, int attemptsExhausted, String lastFailureReason) {
        super("Step '" + stepName + "' failed after " + attemptsExhausted + " retries: " + lastFailureReason);
        this.stepName = stepName;
        this.errorType = PipelineErrorType.UNKNOWN;
    }

    public String getStepName() {
        return stepName;
    }

    public PipelineErrorType getErrorType() {
        return errorType;
    }
}
