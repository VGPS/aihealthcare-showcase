package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.ApiKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} integration tests for {@link ApiKeyAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
@DataJpaTest
@Import(ApiKeyAdapter.class)
class ApiKeyAdapterTest {

    @Autowired
    private ApiKeyAdapter adapter;

    private ApiKey createKey(String id, String owner, String name, String hash) {
        return new ApiKey(id, owner, name, "aih_test", hash, true, Instant.now());
    }

    @Test
    @DisplayName("save and findByKeyHash round-trip")
    void save_findByKeyHash_roundTrip() {
        ApiKey key = createKey("k1", "user@test.com", "My Key", "hash-abc-123");
        adapter.save(key);

        Optional<ApiKey> found = adapter.findByKeyHash("hash-abc-123");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("k1");
        assertThat(found.get().ownerEmail()).isEqualTo("user@test.com");
        assertThat(found.get().name()).isEqualTo("My Key");
    }

    @Test
    @DisplayName("findByKeyHash returns empty for unknown hash")
    void findByKeyHash_unknownHash_returnsEmpty() {
        Optional<ApiKey> found = adapter.findByKeyHash("nonexistent-hash");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findAllByOwnerEmail returns only owned keys")
    void findAllByOwnerEmail_filtersCorrectly() {
        adapter.save(createKey("k1", "alice@test.com", "Key A", "hash-a"));
        adapter.save(createKey("k2", "alice@test.com", "Key B", "hash-b"));
        adapter.save(createKey("k3", "bob@test.com", "Key C", "hash-c"));

        List<ApiKey> aliceKeys = adapter.findAllByOwnerEmail("alice@test.com");
        assertThat(aliceKeys).hasSize(2);
    }

    @Test
    @DisplayName("deleteById removes the key")
    void deleteById_removesKey() {
        adapter.save(createKey("k1", "user@test.com", "Temp Key", "hash-temp"));
        assertThat(adapter.existsById("k1")).isTrue();

        adapter.deleteById("k1");
        assertThat(adapter.existsById("k1")).isFalse();
    }

    @Test
    @DisplayName("existsById returns false for unknown id")
    void existsById_unknownId_returnsFalse() {
        assertThat(adapter.existsById("nonexistent")).isFalse();
    }
}
