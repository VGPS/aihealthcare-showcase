package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AppUserEntity}.
 *
 * <p>Email is the primary key, so {@code findById(email)} serves as the lookup
 * by email address.  {@code findByEmail} is declared explicitly for readability
 * at the call sites in {@link AppUserAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-28
 * @updated 2026-05-28
 */
public interface AppUserRepository extends JpaRepository<AppUserEntity, String> {

    /**
     * Finds a user by email address.
     *
     * @param email The email address to search for.
     * @return An {@link Optional} containing the entity, or empty if not found.
     */
    Optional<AppUserEntity> findByEmail(String email);
}
