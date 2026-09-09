package com.wgblackmon.aihealthcare.domain.port.outbound;

/**
 * Outbound port for sending administrative notifications when system-level
 * failures occur (e.g. LLM model unavailable, API key expired).
 *
 * <p>Implementations live in {@code infrastructure.delivery}. The domain
 * service calls this port to notify the admin without knowing the delivery
 * mechanism (email, Slack, etc.).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-10
 * @updated 2026-09-08 — ED-2 notifyScheduleDeactivated added
 */
public interface AdminNotificationPort {

    /**
     * Notifies the admin that an AI model synthesis call failed.
     *
     * @param providerName human-readable provider name (e.g. "Claude", "GPT")
     * @param modelId      the specific model identifier that failed
     * @param query        the user query that triggered the failure
     * @param cause        the exception that caused the failure
     */
    void notifyModelFailure(String providerName, String modelId, String query, Exception cause);

    /**
     * Notifies the admin that a push schedule was auto-deactivated due to
     * consecutive failures exceeding the configured threshold.
     *
     * @param scheduleId the schedule that was deactivated
     * @param ownerEmail the schedule owner
     * @param lastError  the error from the most recent failed run
     */
    void notifyScheduleDeactivated(String scheduleId, String ownerEmail, String lastError);
}
