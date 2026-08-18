package com.wgblackmon.aihealthcare.domain.exception;

/**
 * Thrown when a password reset token is missing, expired, or already used.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-08-17
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("This password reset link is invalid or has expired.");
    }
}
