package com.wgblackmon.aihealthcare.domain.exception;

/**
 * Thrown when attempting to add a subscriber whose email address is already
 * registered in the system.
 *
 * <p>Maps to HTTP 409 Conflict in the web layer via
 * {@link com.wgblackmon.aihealthcare.web.controller.GlobalExceptionHandler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
public class DuplicateSubscriberException extends RuntimeException {

    private final String email;

    public DuplicateSubscriberException(String email) {
        super("A subscriber already exists for email: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
