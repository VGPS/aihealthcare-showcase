package com.wgblackmon.aihealthcare.domain.exception;

/**
 * Thrown when an evaluation or comparison lookup fails because no record
 * exists for the given identifier.
 *
 * <p>Maps to HTTP 404 Not Found in the web layer via
 * {@link com.wgblackmon.aihealthcare.web.controller.GlobalExceptionHandler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public class EvaluationNotFoundException extends RuntimeException {

    private final String evaluationId;

    public EvaluationNotFoundException(String evaluationId) {
        super("No evaluation found for id: " + evaluationId);
        this.evaluationId = evaluationId;
    }

    public String getEvaluationId() {
        return evaluationId;
    }
}
