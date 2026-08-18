package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.PasswordResetToken;

import java.util.Optional;

/**
 * Outbound port for persisting and looking up password reset tokens.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-08-17
 */
public interface PasswordResetPort {

    /**
     * Persists a new token or overwrites an existing one with the same token value.
     *
     * @param token The token to persist.
     */
    void save(PasswordResetToken token);

    /**
     * Looks up a token by its value.
     *
     * @param token The token string to search for.
     * @return An {@link Optional} containing the token, or empty if not found.
     */
    Optional<PasswordResetToken> findByToken(String token);
}
