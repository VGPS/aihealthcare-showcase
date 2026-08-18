package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link PasswordResetTokenEntity}.
 *
 * <p>{@code findById} (inherited) is the primary lookup — the token string is
 * the primary key.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-08-17
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, String> {
}
