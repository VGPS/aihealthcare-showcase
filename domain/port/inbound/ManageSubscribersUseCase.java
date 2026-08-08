package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;

import java.util.List;

/**
 * Inbound port for subscriber lifecycle management.
 *
 * <p>Drives the three subscriber operations exposed by the REST API:
 * add, remove, and list.  Business rules (duplicate detection, not-found
 * guard) are enforced by the implementation in the application layer.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
public interface ManageSubscribersUseCase {

    /**
     * Registers a new subscriber.
     *
     * @param email Subscriber's email address; must be unique.
     * @param name  Subscriber's display name.
     * @return The newly created {@link Subscriber} record.
     * @throws com.wgblackmon.aihealthcare.domain.exception.DuplicateSubscriberException
     *         if the email is already registered.
     * @throws IllegalArgumentException if email or name is blank.
     */
    Subscriber addSubscriber(String email, String name);

    /**
     * Removes an existing subscriber by email.
     *
     * @param email The email address of the subscriber to remove.
     * @throws com.wgblackmon.aihealthcare.domain.exception.SubscriberNotFoundException
     *         if no subscriber exists for the given email.
     */
    void removeSubscriber(String email);

    /**
     * Returns all subscribers (active and inactive).
     *
     * @return List of all subscribers; never {@code null}, may be empty.
     */
    List<Subscriber> listSubscribers();
}
