package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code sso_provisioning_events} table.
 *
 * <p>Stores an audit record for each SSO provisioning action (account
 * creation, linking, or routine login).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
@Entity
@Table(name = "sso_provisioning_events")
public class SsoProvisioningEventEntity {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "registration_id", nullable = false, length = 100)
    private String registrationId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected SsoProvisioningEventEntity() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getRegistrationId() { return registrationId; }
    public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
