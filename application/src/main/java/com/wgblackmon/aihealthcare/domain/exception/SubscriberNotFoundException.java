package com.wgblackmon.aihealthcare.domain.exception;

/**
 * Thrown when a subscriber operation references an email address for which no
 * active subscription exists.
 *
 * <p>Maps to HTTP 404 Not Found in the web layer via
 * {@link com.wgblackmon.aihealthcare.web.controller.GlobalExceptionHandler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
public class SubscriberNotFoundException extends RuntimeException {

    private final String email;

    public SubscriberNotFoundException(String email) {
        super("No subscriber found for email: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
