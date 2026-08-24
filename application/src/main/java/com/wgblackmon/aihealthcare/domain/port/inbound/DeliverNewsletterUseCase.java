package com.wgblackmon.aihealthcare.domain.port.inbound;

/**
 * Inbound port for triggering newsletter delivery.
 *
 * <p>Implementations look up the specified newsletter run, retrieve all active
 * subscribers, and dispatch the content via the configured delivery adapter.
 * The run's status is updated to {@code SENT} on successful delivery.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-04-13
 * @updated 2026-08-24
 */
public interface DeliverNewsletterUseCase {

    /**
     * Delivers the newsletter run identified by {@code runId} to ENTERPRISE,
     * SUBSCRIBER, and DEMO tier subscribers. Does not touch the FREE-tier
     * digest — see {@link #deliverDigest()}, which runs on its own schedule.
     *
     * @param runId The ID of the newsletter run to deliver.
     * @return the total number of recipients the newsletter was sent to.
     * @throws com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException
     *         if no run exists for the given {@code runId}.
     */
    int deliver(String runId);

    /**
     * Builds a fresh FREE-tier digest and delivers it to all active FREE
     * subscribers. Independent of {@link #deliver(String)} so the digest can
     * run on its own (daily) cadence regardless of the paid newsletter's schedule.
     *
     * @return the number of FREE subscribers the digest was sent to; 0 if there
     *         were no FREE subscribers or no articles to include.
     */
    int deliverDigest();
}
