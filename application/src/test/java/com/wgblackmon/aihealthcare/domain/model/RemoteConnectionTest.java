package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RemoteConnection} — HTTPS enforcement and
 * basic construction.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class RemoteConnectionTest {

    @Test
    void httpsUrl_accepted() {
        RemoteConnection conn = new RemoteConnection(
                "conn-1", "user@example.com", "Acme API",
                RemoteConnectionKind.HTTPS_JSON, "https://api.acme.com/data",
                RemoteAuthType.BEARER, null, "ACME_API_TOKEN", true);

        assertThat(conn.baseUrl()).isEqualTo("https://api.acme.com/data");
        assertThat(conn.authType()).isEqualTo(RemoteAuthType.BEARER);
    }

    @Test
    void httpUrl_rejected() {
        assertThatThrownBy(() -> new RemoteConnection(
                "conn-1", "user@example.com", "Insecure",
                RemoteConnectionKind.HTTPS_JSON, "http://api.acme.com/data",
                RemoteAuthType.NONE, null, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void ftpUrl_rejected() {
        assertThatThrownBy(() -> new RemoteConnection(
                "conn-1", "user@example.com", "FTP",
                RemoteConnectionKind.HTTPS_JSON, "ftp://files.acme.com/",
                RemoteAuthType.NONE, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullBaseUrl_accepted() {
        RemoteConnection conn = new RemoteConnection(
                "conn-1", "user@example.com", "Pending",
                RemoteConnectionKind.HTTPS_JSON, null,
                RemoteAuthType.NONE, null, null, false);

        assertThat(conn.baseUrl()).isNull();
    }

    @Test
    void secretRefIsName_notValue() {
        RemoteConnection conn = new RemoteConnection(
                "conn-1", "user@example.com", "Token API",
                RemoteConnectionKind.HTTPS_JSON, "https://api.acme.com",
                RemoteAuthType.API_KEY_HEADER, "X-Api-Key", "ACME_SECRET_ENV_VAR", true);

        assertThat(conn.secretRef()).isEqualTo("ACME_SECRET_ENV_VAR");
        assertThat(conn.headerName()).isEqualTo("X-Api-Key");
    }

    @Test
    void apiKeyHeaderAuth_withHeaderName() {
        RemoteConnection conn = new RemoteConnection(
                "conn-1", "user@example.com", "Custom Header",
                RemoteConnectionKind.HTTPS_JSON, "https://api.example.com",
                RemoteAuthType.API_KEY_HEADER, "X-Custom-Key", "MY_KEY_REF", true);

        assertThat(conn.authType()).isEqualTo(RemoteAuthType.API_KEY_HEADER);
        assertThat(conn.headerName()).isEqualTo("X-Custom-Key");
    }
}
