package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.RemoteAuthType;
import com.wgblackmon.aihealthcare.domain.model.RemoteConnection;
import com.wgblackmon.aihealthcare.domain.model.RemoteConnectionKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RemoteConnectionAdapter} — round-trip persistence,
 * ownership isolation, and secure delete behaviour.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@DataJpaTest
class RemoteConnectionAdapterTest {

    @Autowired
    private EnterpriseRemoteConnectionRepository repository;

    private RemoteConnectionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RemoteConnectionAdapter(repository);
    }

    private RemoteConnection connection(String id, String ownerEmail) {
        return new RemoteConnection(
                id, ownerEmail, "My FHIR Server",
                RemoteConnectionKind.HTTPS_JSON,
                "https://fhir.example.com/api",
                RemoteAuthType.BEARER,
                null, "FHIR_TOKEN_ENV_VAR", true
        );
    }

    @Test
    void save_roundTrip() {
        RemoteConnection saved = adapter.save(connection("c1", "alice@test.com"));
        assertThat(saved.connectionId()).isEqualTo("c1");
        assertThat(saved.ownerEmail()).isEqualTo("alice@test.com");
        assertThat(saved.kind()).isEqualTo(RemoteConnectionKind.HTTPS_JSON);
        assertThat(saved.baseUrl()).isEqualTo("https://fhir.example.com/api");
        assertThat(saved.authType()).isEqualTo(RemoteAuthType.BEARER);
        assertThat(saved.secretRef()).isEqualTo("FHIR_TOKEN_ENV_VAR");
    }

    @Test
    void findByConnectionIdAndOwnerEmail_returnsOwnedConnection() {
        adapter.save(connection("c1", "alice@test.com"));

        Optional<RemoteConnection> found = adapter.findByConnectionIdAndOwnerEmail("c1", "alice@test.com");
        assertThat(found).isPresent();
        assertThat(found.get().connectionId()).isEqualTo("c1");
    }

    @Test
    void findByConnectionIdAndOwnerEmail_crossOwnerReturnsEmpty() {
        adapter.save(connection("c1", "alice@test.com"));

        Optional<RemoteConnection> found = adapter.findByConnectionIdAndOwnerEmail("c1", "bob@test.com");
        assertThat(found).isEmpty();
    }

    @Test
    void findByOwnerEmail_returnsOnlyOwnedConnections() {
        adapter.save(connection("c1", "alice@test.com"));
        adapter.save(connection("c2", "alice@test.com"));
        adapter.save(connection("c3", "bob@test.com"));

        List<RemoteConnection> aliceConns = adapter.findByOwnerEmail("alice@test.com");
        assertThat(aliceConns).hasSize(2);
        assertThat(aliceConns).extracting(RemoteConnection::ownerEmail)
                .containsOnly("alice@test.com");
    }

    @Test
    void delete_removesOwnedConnection() {
        adapter.save(connection("c1", "alice@test.com"));
        assertThat(adapter.findByConnectionIdAndOwnerEmail("c1", "alice@test.com")).isPresent();

        adapter.delete("c1", "alice@test.com");
        assertThat(adapter.findByConnectionIdAndOwnerEmail("c1", "alice@test.com")).isEmpty();
    }

    @Test
    void delete_doesNothingForWrongOwner() {
        adapter.save(connection("c1", "alice@test.com"));

        adapter.delete("c1", "bob@test.com");
        assertThat(adapter.findByConnectionIdAndOwnerEmail("c1", "alice@test.com")).isPresent();
    }

    @Test
    void save_allAuthTypes_roundTrip() {
        adapter.save(new RemoteConnection(
                "c-none", "alice@test.com", "No Auth",
                RemoteConnectionKind.HTTPS_JSON, "https://public.example.com/api",
                RemoteAuthType.NONE, null, null, true));
        adapter.save(new RemoteConnection(
                "c-apikey", "alice@test.com", "API Key",
                RemoteConnectionKind.HTTPS_JSON, "https://keyed.example.com/api",
                RemoteAuthType.API_KEY_HEADER, "X-Api-Key", "MY_API_KEY_ENV", true));

        RemoteConnection none = adapter.findByConnectionIdAndOwnerEmail("c-none", "alice@test.com").orElseThrow();
        assertThat(none.authType()).isEqualTo(RemoteAuthType.NONE);
        assertThat(none.headerName()).isNull();

        RemoteConnection apiKey = adapter.findByConnectionIdAndOwnerEmail("c-apikey", "alice@test.com").orElseThrow();
        assertThat(apiKey.authType()).isEqualTo(RemoteAuthType.API_KEY_HEADER);
        assertThat(apiKey.headerName()).isEqualTo("X-Api-Key");
        assertThat(apiKey.secretRef()).isEqualTo("MY_API_KEY_ENV");
    }
}
