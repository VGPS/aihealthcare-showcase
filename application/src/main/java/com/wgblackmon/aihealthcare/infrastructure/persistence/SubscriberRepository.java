package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SubscriberEntity}.
 *
 * <p>Email is the primary key, so {@code findById(email)} serves as the lookup
 * by email address.  {@code findAllByActiveTrueAndTier} limits mailing-list
 * queries to active subscriptions filtered by tier.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-05-23
 */
public interface SubscriberRepository extends JpaRepository<SubscriberEntity, String> {

    /**
     * Finds a subscriber by email address.
     * Equivalent to {@code findById} since email is the primary key; named
     * explicitly for readability at the call sites in {@link SubscriberAdapter}.
     *
     * @param email The email address to search for.
     * @return An {@link Optional} containing the entity, or empty if not found.
     */
    Optional<SubscriberEntity> findByEmail(String email);

    /**
     * Returns all active subscribers with the given tier.
     * Used to target content delivery by subscription level.
     *
     * @param tier The tier string (FREE, SUBSCRIBER).
     * @return List of matching subscriber entities; never {@code null}.
     */
    List<SubscriberEntity> findAllByActiveTrueAndTier(String tier);
}
