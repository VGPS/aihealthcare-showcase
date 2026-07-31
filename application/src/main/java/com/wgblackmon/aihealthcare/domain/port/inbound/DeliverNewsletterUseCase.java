package com.wgblackmon.aihealthcare.domain.port.inbound;

/**
 * Inbound port for triggering newsletter delivery.
 *
 * <p>Implementations look up the specified newsletter run, retrieve all active
 * subscribers, and dispatch the content via the configured delivery adapter.
 * The run's status is updated to {@code SENT} on successful delivery.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
public interface DeliverNewsletterUseCase {

    /**
     * Delivers the newsletter run identified by {@code runId} to all active subscribers.
     *
     * @param runId The ID of the newsletter run to deliver.
     * @return the total number of recipients the newsletter was sent to.
     * @throws com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException
     *         if no run exists for the given {@code runId}.
     */
    int deliver(String runId);
}
