package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.AppUser;

/**
 * Inbound port for self-registration of new DEMO users.
 *
 * <p>Creates both an {@link AppUser} (for login) and a newsletter subscriber
 * record (for email delivery) with tier=DEMO and a 7-day expiration window.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
public interface RegisterUserUseCase {

    /**
     * Registers a new DEMO user.
     *
     * @param email       the user's email address.
     * @param displayName the user's display name.
     * @param rawPassword the plaintext password (will be hashed before storage).
     * @return the created {@link AppUser} with tier=DEMO and demoExpiresAt set.
     * @throws com.wgblackmon.aihealthcare.domain.exception.DuplicateUserException
     *         if the email is already in use.
     */
    AppUser register(String email, String displayName, String rawPassword);
}
