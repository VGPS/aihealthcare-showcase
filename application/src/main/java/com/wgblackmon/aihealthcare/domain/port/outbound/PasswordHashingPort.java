package com.wgblackmon.aihealthcare.domain.port.outbound;

/**
 * Outbound port for hashing raw passwords.
 *
 * <p>Keeps password-encoding implementation details (e.g. BCrypt) out of
 * the domain layer.  Implementations live in {@code infrastructure.config}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
public interface PasswordHashingPort {

    /**
     * Hashes a raw password into a storage-safe encoded form.
     *
     * @param rawPassword the plaintext password; must not be blank.
     * @return the encoded password hash.
     */
    String hash(String rawPassword);
}
