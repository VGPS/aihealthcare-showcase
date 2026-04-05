package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.exception.NoArticlesFoundException;
import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.web.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralised exception-to-HTTP-status mapping for the newsletter API.
 *
 * <p>{@code @RestControllerAdvice} is Spring MVC's mechanism for applying
 * cross-cutting exception handling across all controllers without polluting
 * them with try/catch blocks.  When a controller method (or anything it calls)
 * throws a recognised exception, Spring routes it here before sending the
 * response to the client.
 *
 * <p>Mappings:
 * <ul>
 *   <li>{@link RunNotFoundException} → <b>404 Not Found</b> — the run or draft ID
 *       doesn't exist in the in-memory store.</li>
 *   <li>{@link NoArticlesFoundException} → <b>422 Unprocessable Entity</b> — the
 *       ingestion run exists but produced no articles to summarize.</li>
 *   <li>{@link IllegalArgumentException} → <b>400 Bad Request</b> — a required field
 *       was blank or an argument failed validation in the service layer.</li>
 * </ul>
 *
 * <p>All error responses use the {@link ErrorResponse} record so clients receive a
 * consistent JSON shape: {@code {"code":"…","message":"…"}}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles lookups for run or draft IDs that do not exist.
     * Maps to HTTP 404 Not Found.
     */
    @ExceptionHandler(RunNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRunNotFound(RunNotFoundException ex) {
        log.debug("handleRunNotFound() | ex={}", ex.getMessage());
        log.warn("handleRunNotFound() | Run or draft not found: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("NOT_FOUND", ex.getMessage());
        log.debug("handleRunNotFound() | return=404");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handles generation requests where an ingestion run completed but returned
     * no articles.  Maps to HTTP 422 Unprocessable Entity.
     */
    @ExceptionHandler(NoArticlesFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoArticles(NoArticlesFoundException ex) {
        log.debug("handleNoArticles() | ex={}", ex.getMessage());
        log.warn("handleNoArticles() | No articles for runId={}", ex.getRunId());
        ErrorResponse body = new ErrorResponse("NO_ARTICLES", ex.getMessage());
        log.debug("handleNoArticles() | return=422");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    /**
     * Handles validation failures from the service layer (blank required fields, etc.).
     * Maps to HTTP 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.debug("handleBadRequest() | ex={}", ex.getMessage());
        log.warn("handleBadRequest() | Bad request: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("BAD_REQUEST", ex.getMessage());
        log.debug("handleBadRequest() | return=400");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
