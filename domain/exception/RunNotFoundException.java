package com.wgblackmon.aihealthcare.domain.exception;

/**
 * Thrown when a newsletter generation request references a {@code runId} for which
 * no ingestion run exists in the current store.
 *
 * <p>Maps to HTTP 404 in the web layer.
 */
public class RunNotFoundException extends RuntimeException {

    private final String runId;

    public RunNotFoundException(String runId) {
        super("No ingestion run found for runId: " + runId);
        this.runId = runId;
    }

    public String getRunId() {
        return runId;
    }
}
