package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code sso_identity_providers} table.
 *
 * <p>Stores the SAML2 IdP configuration for a single enterprise tenant.
 * The {@code registrationId} slug is the natural primary key and doubles
 * as the Spring Security SAML registration identifier.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
@Entity
@Table(name = "sso_identity_providers")
public class SsoIdentityProviderEntity {

    @Id
    @Column(name = "registration_id", length = 100)
    private String registrationId;

    @Column(nullable = false)
    private String label;

    @Column(name = "entity_id", nullable = false, length = 512)
    private String entityId;

    @Column(name = "sso_url", nullable = false, length = 1024)
    private String ssoUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String certificate;

    @Column(name = "metadata_url", length = 1024)
    private String metadataUrl;

    @Column(name = "email_attribute", nullable = false, length = 100)
    private String emailAttribute;

    @Column(name = "display_name_attribute", nullable = false, length = 100)
    private String displayNameAttribute;

    @Column(name = "default_tier", nullable = false, length = 20)
    private String defaultTier;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SsoIdentityProviderEntity() {}

    public String getRegistrationId() { return registrationId; }
    public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getSsoUrl() { return ssoUrl; }
    public void setSsoUrl(String ssoUrl) { this.ssoUrl = ssoUrl; }

    public String getCertificate() { return certificate; }
    public void setCertificate(String certificate) { this.certificate = certificate; }

    public String getMetadataUrl() { return metadataUrl; }
    public void setMetadataUrl(String metadataUrl) { this.metadataUrl = metadataUrl; }

    public String getEmailAttribute() { return emailAttribute; }
    public void setEmailAttribute(String emailAttribute) { this.emailAttribute = emailAttribute; }

    public String getDisplayNameAttribute() { return displayNameAttribute; }
    public void setDisplayNameAttribute(String displayNameAttribute) { this.displayNameAttribute = displayNameAttribute; }

    public String getDefaultTier() { return defaultTier; }
    public void setDefaultTier(String defaultTier) { this.defaultTier = defaultTier; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
