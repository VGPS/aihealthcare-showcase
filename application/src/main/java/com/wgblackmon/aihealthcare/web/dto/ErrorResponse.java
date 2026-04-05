package com.wgblackmon.aihealthcare.web.dto;

/**
 * Standard error body returned by the API when a request cannot be fulfilled.
 *
 * <p>Used by {@link com.wgblackmon.aihealthcare.web.controller.GlobalExceptionHandler}
 * for all error responses.  The {@code code} field is a machine-readable token
 * suitable for client-side branching; {@code message} is human-readable detail.
 *
 * @param code    Short machine-readable error code (e.g. {@code "NOT_FOUND"},
 *                {@code "BAD_REQUEST"}, {@code "NO_ARTICLES"}).
 * @param message Human-readable description of what went wrong.
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
public record ErrorResponse(
        String code,
        String message
) {}
