package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a single-use password reset token.
 *
 * <p>Issued by {@link com.wgblackmon.aihealthcare.domain.service.PasswordResetService}
 * when a user requests a password reset, and consumed exactly once when the
 * new password is set. The {@code token} is the natural business key and
 * serves as the unique identifier in the persistence layer.
 *
 * @param token     Opaque, unguessable token string (UUID); the unique lookup key. Must not be blank.
 * @param email     The account email address this token grants a reset for. Must not be blank.
 * @param expiresAt Instant after which the token is no longer valid.
 * @param used      {@code true} once the token has been consumed by a successful reset.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-08-17
 */
public record PasswordResetToken(
        String  token,
        String  email,
        Instant expiresAt,
        boolean used
) {
    /** Compact canonical constructor — validates required fields. */
    public PasswordResetToken {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
    }
}
