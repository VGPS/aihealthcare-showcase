package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SubscriberAdapter} using an embedded H2 database.
 *
 * <p>{@code @DataJpaTest} loads only the JPA slice (entities + repositories) and
 * auto-configures H2.  {@code @Import(SubscriberAdapter.class)} pulls in the adapter
 * so we test the full save/find/delete round-trip, not just the repository in isolation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
@DataJpaTest
@Import(SubscriberAdapter.class)
class SubscriberAdapterTest {

    @Autowired
    private SubscriberAdapter adapter;

    private static final Instant NOW = Instant.parse("2026-04-13T10:00:00Z");

    private static Subscriber subscriber(String email, String name, boolean active) {
        return new Subscriber(email, name, active, NOW, null, null, null, null);
    }

    // -------------------------------------------------------------------------
    // save() + findByEmail()
    // -------------------------------------------------------------------------

    @Test
    void save_thenFindByEmail_returnsSubscriber() {
        adapter.save(subscriber("a@example.com", "Alice", true));

        Optional<Subscriber> result = adapter.findByEmail("a@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().email()).isEqualTo("a@example.com");
        assertThat(result.get().name()).isEqualTo("Alice");
        assertThat(result.get().active()).isTrue();
    }

    @Test
    void findByEmail_unknownEmail_returnsEmpty() {
        Optional<Subscriber> result = adapter.findByEmail("nobody@example.com");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findAll()
    // -------------------------------------------------------------------------

    @Test
    void findAll_returnsAllSubscribers() {
        int baseline = adapter.findAll().size();
        adapter.save(subscriber("a@example.com", "Alice", true));
        adapter.save(subscriber("b@example.com", "Bob",   false));

        List<Subscriber> result = adapter.findAll();

        assertThat(result).hasSize(baseline + 2);
    }

    // -------------------------------------------------------------------------
    // deleteByEmail()
    // -------------------------------------------------------------------------

    @Test
    void deleteByEmail_removesSubscriber() {
        adapter.save(subscriber("a@example.com", "Alice", true));

        adapter.deleteByEmail("a@example.com");

        assertThat(adapter.findByEmail("a@example.com")).isEmpty();
    }

    @Test
    void deleteByEmail_unknownEmail_doesNotThrow() {
        // Should be a no-op
        adapter.deleteByEmail("ghost@example.com");
    }
}
