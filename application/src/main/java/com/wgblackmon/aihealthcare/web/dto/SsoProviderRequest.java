package com.wgblackmon.aihealthcare.web.dto;

/**
 * REST request record for creating or updating an SSO Identity Provider.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public record SsoProviderRequest(
        String registrationId,
        String label,
        String entityId,
        String ssoUrl,
        String certificate,
        String metadataUrl,
        String emailAttribute,
        String displayNameAttribute,
        boolean active) {
}
