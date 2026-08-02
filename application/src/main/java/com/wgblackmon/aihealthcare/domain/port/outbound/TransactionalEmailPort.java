package com.wgblackmon.aihealthcare.domain.port.outbound;

/**
 * Outbound port for sending transactional emails (welcome, expiration, etc.).
 *
 * <p>Unlike newsletter delivery which sends bulk emails via
 * {@link NewsletterDeliveryPort}, this port handles individual one-off
 * transactional messages triggered by user actions or system events.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-31
 * @updated 2026-08-01
 */
public interface TransactionalEmailPort {

    /**
     * Sends a welcome email to a newly registered user.
     *
     * @param email    the recipient email address.
     * @param name     the user's display name.
     * @param demoDays the number of days in the demo period.
     */
    void sendWelcome(String email, String name, int demoDays);

    /**
     * Sends a demo expiration notification to a user whose trial has ended.
     *
     * @param email the recipient email address.
     * @param name  the user's display name.
     */
    void sendDemoExpiration(String email, String name);

    /**
     * Notifies the admin that a new user has registered.
     *
     * @param userEmail   the new user's email address.
     * @param displayName the new user's display name.
     * @param tier        the tier assigned to the new user.
     */
    void notifyAdminNewRegistration(String userEmail, String displayName, String tier);
}
