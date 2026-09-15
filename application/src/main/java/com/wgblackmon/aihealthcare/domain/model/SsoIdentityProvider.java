package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Configuration record for a SAML2 Identity Provider (IdP) tenant.
 *
 * <p>Each enterprise customer gets one registration, identified by a
 * URL-safe slug (e.g. "mayo-clinic"). The record holds the IdP's SAML
 * metadata (entity ID, SSO URL, signing certificate) plus configurable
 * attribute mappings for extracting user identity from SAML assertions.
 *
 * @param registrationId         URL-safe slug used as the SAML registration ID (e.g. "mayo-clinic").
 * @param label                  Human-readable display name shown on the login page.
 * @param entityId               The IdP's SAML entity ID from its metadata.
 * @param ssoUrl                 The IdP's Single Sign-On URL (HTTP-POST or HTTP-Redirect binding).
 * @param certificate            PEM-encoded X.509 signing certificate for verifying SAML assertions.
 * @param metadataUrl            Optional URL to auto-fetch IdP metadata (null if manually configured).
 * @param emailAttribute         SAML attribute name that carries the user's email address.
 * @param displayNameAttribute   SAML attribute name that carries the user's display name.
 * @param defaultTier            Subscription tier assigned to auto-provisioned users.
 * @param active                 Whether this IdP is enabled for authentication.
 * @param createdAt              Timestamp when this IdP configuration was created.
 * @param updatedAt              Timestamp of the last configuration change.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public record SsoIdentityProvider(
        String           registrationId,
        String           label,
        String           entityId,
        String           ssoUrl,
        String           certificate,
        String           metadataUrl,
        String           emailAttribute,
        String           displayNameAttribute,
        SubscriptionTier defaultTier,
        boolean          active,
        Instant          createdAt,
        Instant          updatedAt
) {
    private static final String REGISTRATION_ID_PATTERN = "^[a-z0-9][a-z0-9-]*[a-z0-9]$";

    public SsoIdentityProvider {
        if (registrationId == null || registrationId.isBlank()) {
            throw new IllegalArgumentException("registrationId must not be blank");
        }
        if (!registrationId.matches(REGISTRATION_ID_PATTERN)) {
            throw new IllegalArgumentException(
                    "registrationId must be lowercase alphanumeric with hyphens: " + registrationId);
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("entityId must not be blank");
        }
        if (ssoUrl == null || ssoUrl.isBlank()) {
            throw new IllegalArgumentException("ssoUrl must not be blank");
        }
        if (certificate == null || certificate.isBlank()) {
            throw new IllegalArgumentException("certificate must not be blank");
        }
        if (emailAttribute == null || emailAttribute.isBlank()) {
            emailAttribute = "email";
        }
        if (displayNameAttribute == null || displayNameAttribute.isBlank()) {
            displayNameAttribute = "displayName";
        }
        if (defaultTier == null) {
            defaultTier = SubscriptionTier.ENTERPRISE;
        }
    }
}
