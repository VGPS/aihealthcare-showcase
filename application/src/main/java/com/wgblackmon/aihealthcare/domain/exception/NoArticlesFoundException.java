package com.wgblackmon.aihealthcare.domain.exception;

/**
 * Thrown when a newsletter generation request references a valid {@code runId} but
 * the associated ingestion run produced no processable articles.
 *
 * <p>Maps to HTTP 422 in the web layer.
 */
public class NoArticlesFoundException extends RuntimeException {

    private final String runId;

    public NoArticlesFoundException(String runId) {
        super("Ingestion run '" + runId + "' completed but contains no processable articles");
        this.runId = runId;
    }

    public String getRunId() {
        return runId;
    }
}
