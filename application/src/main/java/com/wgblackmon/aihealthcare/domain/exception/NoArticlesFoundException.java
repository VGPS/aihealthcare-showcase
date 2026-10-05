package com.wgblackmon.aihealthcare.domain.exception;

/**
 * Thrown when a newsletter generation request references a valid {@code runId} but
 * the associated ingestion run produced no processable articles.
 *
 * <p>Maps to HTTP 422 in the web layer.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2025-01-27
 * @updated 2026-10-05
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
