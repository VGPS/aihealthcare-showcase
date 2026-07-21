package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.AppUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and retrieving application users.
 *
 * <p>Implementations live in {@code infrastructure.persistence}.  The domain
 * and application layers depend only on this interface and never on JPA or any
 * other storage technology.
 *
 * <p>Email is the natural key for users and serves as the lookup identifier.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-28
 * @updated 2026-05-31
 */
public interface AppUserPort {

    /**
     * Looks up a single user by email address.
     *
     * @param email The email address to search for.
     * @return An {@link Optional} containing the user, or empty if not found.
     */
    Optional<AppUser> findByEmail(String email);

    /**
     * Returns all registered users.
     *
     * @return A list of all users; empty list if none exist.
     */
    List<AppUser> findAll();

    /**
     * Persists a new user or overwrites an existing one with the same email.
     *
     * @param user The user to persist.
     */
    void save(AppUser user);

    /**
     * Returns all users with the given tier whose demo has expired before the
     * given cutoff time.  Used by the nightly demo expiration scheduler.
     *
     * @param tier   the tier to filter by (typically DEMO).
     * @param before the cutoff timestamp; users whose demoExpiresAt is before
     *               this value are returned.
     * @return matching users; may be empty.
     */
    List<AppUser> findByTierAndDemoExpiresAtBefore(String tier, Instant before);
}
