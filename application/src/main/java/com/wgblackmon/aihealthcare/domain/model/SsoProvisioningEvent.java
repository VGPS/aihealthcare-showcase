package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Audit record for SSO provisioning activity.
 *
 * <p>Captures each meaningful SSO authentication event: first-time account
 * creation, linking an existing account to an IdP, or a routine login.
 * Used for audit trails and admin monitoring of SSO adoption.
 *
 * @param eventId          Unique event identifier.
 * @param registrationId   The IdP registration slug that authenticated the user.
 * @param email            The user's email address extracted from the SAML assertion.
 * @param action           The provisioning action taken (CREATED, LINKED, or LOGIN).
 * @param occurredAt       Timestamp of the event.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public record SsoProvisioningEvent(
        String                eventId,
        String                registrationId,
        String                email,
        SsoProvisioningAction action,
        Instant               occurredAt
) {
    public SsoProvisioningEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (registrationId == null || registrationId.isBlank()) {
            throw new IllegalArgumentException("registrationId must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
    }
}
