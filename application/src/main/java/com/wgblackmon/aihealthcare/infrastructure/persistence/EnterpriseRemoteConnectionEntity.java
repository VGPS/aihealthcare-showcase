package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for the {@code enterprise_remote_connections} table — customer-registered
 * HTTPS/JSON endpoints for the {@code CUSTOMER_REMOTE} source family.
 *
 * <p>{@code secret_ref} holds the <em>name</em> of an environment variable
 * or Secrets Manager entry, never the credential value itself.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Entity
@Table(name = "enterprise_remote_connections")
public class EnterpriseRemoteConnectionEntity {

    @Id
    @Column(name = "connection_id", length = 36)
    private String connectionId;

    @Column(name = "owner_email", nullable = false, length = 320)
    private String ownerEmail;

    @Column(name = "label", nullable = false, length = 160)
    private String label;

    @Column(name = "kind", nullable = false, length = 24)
    private String kind;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "auth_type", nullable = false, length = 24)
    private String authType;

    @Column(name = "header_name", length = 64)
    private String headerName;

    @Column(name = "secret_ref", length = 256)
    private String secretRef;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required no-arg constructor for JPA. */
    public EnterpriseRemoteConnectionEntity() {}

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }
    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
