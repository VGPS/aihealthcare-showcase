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
 * @updated 2026-07-10
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
}
