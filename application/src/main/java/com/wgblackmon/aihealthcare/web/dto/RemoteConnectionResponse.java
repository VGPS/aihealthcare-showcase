package com.wgblackmon.aihealthcare.web.dto;

import com.wgblackmon.aihealthcare.domain.model.RemoteConnection;

/**
 * Response DTO for a remote HTTPS connection.
 *
 * <p><strong>SECURITY:</strong> {@code secretRef} is intentionally omitted.
 * The response only indicates whether a secret is configured ({@code hasSecret}).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record RemoteConnectionResponse(
        String connectionId,
        String label,
        String kind,
        String baseUrl,
        String authType,
        String headerName,
        boolean hasSecret,
        boolean active
) {

    public static RemoteConnectionResponse from(RemoteConnection conn) {
        return new RemoteConnectionResponse(
                conn.connectionId(),
                conn.label(),
                conn.kind().name(),
                conn.baseUrl(),
                conn.authType().name(),
                conn.headerName(),
                conn.secretRef() != null && !conn.secretRef().isBlank(),
                conn.active());
    }
}
