package com.wgblackmon.aihealthcare.domain.port.inbound;

/**
 * Inbound port for the self-service "forgot password" flow.
 *
 * <p>Since login is by email address (there is no separate username in this
 * system), recovery is a single token-based password reset flow: request a
 * link, then set a new password.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-08-17
 */
public interface PasswordResetUseCase {

    /**
     * Issues a password reset token and emails a reset link, if an account
     * exists for the given email. Silently no-ops for unknown emails so the
     * caller cannot use this endpoint to enumerate registered accounts.
     *
     * @param email the email address to send a reset link to.
     */
    void requestReset(String email);

    /**
     * Checks whether a reset token is present, unused, and not expired.
     *
     * @param token the token string to validate.
     * @return {@code true} if the token can currently be used to reset a password.
     */
    boolean validateToken(String token);

    /**
     * Consumes a reset token and sets a new password for the associated account.
     *
     * @param token       the reset token from the emailed link.
     * @param newPassword the new plaintext password (will be hashed before storage).
     * @throws com.wgblackmon.aihealthcare.domain.exception.InvalidResetTokenException
     *         if the token is missing, expired, or already used.
     */
    void resetPassword(String token, String newPassword);
}
