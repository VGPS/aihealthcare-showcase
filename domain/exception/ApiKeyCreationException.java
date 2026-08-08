package com.wgblackmon.aihealthcare.domain.exception;

/**
 * Thrown when API key creation is denied — either the user's subscription tier
 * does not permit API key creation, or the maximum key count has been reached.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public class ApiKeyCreationException extends RuntimeException {

    private final String reason;

    public ApiKeyCreationException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
