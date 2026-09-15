package com.wgblackmon.aihealthcare.web.dto;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;

import java.time.Instant;

/**
 * REST response record for SSO Identity Provider data.
 *
 * <p>Omits the certificate field for security (not returned in API responses).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public record SsoProviderResponse(
        String registrationId,
        String label,
        String entityId,
        String ssoUrl,
        String emailAttribute,
        String displayNameAttribute,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static SsoProviderResponse from(SsoIdentityProvider idp) {
        return new SsoProviderResponse(
                idp.registrationId(), idp.label(), idp.entityId(), idp.ssoUrl(),
                idp.emailAttribute(), idp.displayNameAttribute(), idp.active(),
                idp.createdAt(), idp.updatedAt());
    }
}
