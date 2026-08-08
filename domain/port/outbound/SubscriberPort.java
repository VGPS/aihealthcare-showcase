package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and retrieving newsletter subscribers.
 *
 * <p>Implementations live in {@code infrastructure.persistence}.  The domain
 * and application layers depend only on this interface and never on JPA or any
 * other storage technology.
 *
 * <p>Email is the natural key for subscribers and is used as both the persistence
 * identifier and the deduplication check.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-04-13
 * @updated 2026-07-31
 */
public interface SubscriberPort {

    /**
     * Persists a new subscriber.  Callers must check for duplicates before calling
     * this method; behaviour on duplicate email is implementation-defined.
     *
     * @param subscriber The subscriber to persist.
     */
    void save(Subscriber subscriber);

    /**
     * Returns all subscribers regardless of active status.
     *
     * @return Unmodifiable list; never {@code null}, may be empty.
     */
    List<Subscriber> findAll();

    /**
     * Looks up a single subscriber by email address.
     *
     * @param email The email address to search for.
     * @return An {@link Optional} containing the subscriber, or empty if not found.
     */
    Optional<Subscriber> findByEmail(String email);

    /**
     * Returns only subscribers whose {@code active} flag is {@code true} and
     * whose tier matches the given value.
     *
     * @param tier The subscription tier to filter by.
     * @return Unmodifiable list; never {@code null}, may be empty.
     */
    List<Subscriber> findAllActiveByTier(SubscriptionTier tier);

    /**
     * Deletes the subscriber record identified by the given email address.
     * No-ops silently if the email does not exist.
     *
     * @param email The email address of the subscriber to delete.
     */
    void deleteByEmail(String email);

    /**
     * Looks up a subscriber by their unique unsubscribe token.
     *
     * @param token The UUID unsubscribe token.
     * @return An {@link Optional} containing the subscriber, or empty if not found.
     */
    Optional<Subscriber> findByUnsubscribeToken(String token);
}
