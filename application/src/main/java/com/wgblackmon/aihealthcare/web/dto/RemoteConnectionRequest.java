package com.wgblackmon.aihealthcare.web.dto;

/**
 * Request body for creating or updating a remote HTTPS connection.
 *
 * <p>{@code secretRef} is the name of an environment variable holding the
 * secret value — never the secret itself.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record RemoteConnectionRequest(
        String label,
        String kind,
        String baseUrl,
        String authType,
        String headerName,
        String secretRef,
        boolean active
) {}
