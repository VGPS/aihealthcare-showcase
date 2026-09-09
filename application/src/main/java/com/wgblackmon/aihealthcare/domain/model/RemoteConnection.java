package com.wgblackmon.aihealthcare.domain.model;

/**
 * A customer-registered remote HTTPS/JSON endpoint for the
 * {@code CUSTOMER_REMOTE} data source family.
 *
 * <p><strong>{@code secretRef} is a name, never a value.</strong> It holds
 * the name of an environment variable (or AWS Secrets Manager entry) whose
 * value is resolved at request time. The resolved value is never logged,
 * never returned by any API, and never rendered in the UI.
 *
 * @param connectionId unique identifier
 * @param ownerEmail   tenancy key
 * @param label        human-readable name
 * @param kind         transport type (HTTPS_JSON only in ED-1)
 * @param baseUrl      must start with {@code https://}
 * @param authType     how credentials are attached
 * @param headerName   header name for API_KEY_HEADER auth (nullable)
 * @param secretRef    env var name or Secrets Manager id (nullable)
 * @param active       whether this connection is usable
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record RemoteConnection(
        String connectionId,
        String ownerEmail,
        String label,
        RemoteConnectionKind kind,
        String baseUrl,
        RemoteAuthType authType,
        String headerName,
        String secretRef,
        boolean active
) {
    public RemoteConnection {
        if (baseUrl != null && !baseUrl.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "RemoteConnection baseUrl must use https, got: " + baseUrl);
        }
    }
}
