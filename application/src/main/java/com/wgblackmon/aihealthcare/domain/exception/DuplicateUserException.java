package com.wgblackmon.aihealthcare.domain.exception;

/**
 * Thrown when attempting to register a user whose email address is already
 * registered as either an app user or a newsletter subscriber.
 *
 * <p>Maps to HTTP 409 Conflict in the web layer via
 * {@link com.wgblackmon.aihealthcare.web.controller.GlobalExceptionHandler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
public class DuplicateUserException extends RuntimeException {

    private final String email;

    public DuplicateUserException(String email) {
        super("A user already exists for email: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
