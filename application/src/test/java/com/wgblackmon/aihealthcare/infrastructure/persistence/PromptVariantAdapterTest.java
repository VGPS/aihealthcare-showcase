package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.exception.PromptVariantNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.PromptVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA slice tests for {@link PromptVariantAdapter}.
 *
 * <p>Uses {@code @DataJpaTest} which loads only the JPA slice (H2, repositories,
 * entity scanning) without the full Spring context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
@DataJpaTest
class PromptVariantAdapterTest {

    @Autowired
    private PromptVariantRepository repository;

    private PromptVariantAdapter adapter;

    private static final PromptVariant VARIANT = new PromptVariant(
            "summarize-v1", "Default V1",
            "Summarize {topic} articles.", "Baseline prompt",
            Instant.parse("2026-04-17T00:00:00Z"));

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        adapter = new PromptVariantAdapter(repository);
    }

    @Test
    void save_and_findByVariantId_roundTrips() {
        adapter.save(VARIANT);

        PromptVariant found = adapter.findByVariantId("summarize-v1");

        assertThat(found.variantId()).isEqualTo("summarize-v1");
        assertThat(found.name()).isEqualTo("Default V1");
        assertThat(found.templateText()).isEqualTo("Summarize {topic} articles.");
        assertThat(found.description()).isEqualTo("Baseline prompt");
        assertThat(found.createdAt()).isEqualTo(Instant.parse("2026-04-17T00:00:00Z"));
    }

    @Test
    void findByVariantId_notFound_throws() {
        assertThatThrownBy(() -> adapter.findByVariantId("unknown"))
                .isInstanceOf(PromptVariantNotFoundException.class);
    }

    @Test
    void findAll_empty_returnsEmptyList() {
        List<PromptVariant> result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_populated_returnsAll() {
        adapter.save(VARIANT);
        adapter.save(new PromptVariant(
                "summarize-v2", "Concise V2",
                "Be concise about {topic}.", "Short version",
                Instant.parse("2026-04-17T01:00:00Z")));

        List<PromptVariant> result = adapter.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void delete_removesVariant() {
        adapter.save(VARIANT);
        adapter.delete("summarize-v1");

        assertThatThrownBy(() -> adapter.findByVariantId("summarize-v1"))
                .isInstanceOf(PromptVariantNotFoundException.class);
    }

    @Test
    void delete_notFound_throws() {
        assertThatThrownBy(() -> adapter.delete("unknown"))
                .isInstanceOf(PromptVariantNotFoundException.class);
    }
}
