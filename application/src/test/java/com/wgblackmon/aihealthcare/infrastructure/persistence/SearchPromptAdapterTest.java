package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice tests for {@link SearchPromptAdapter}.
 *
 * <p>Uses {@code @DataJpaTest} which loads only the JPA slice (H2, repositories,
 * entity scanning) without the full Spring context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
@DataJpaTest
class SearchPromptAdapterTest {

    @Autowired
    private SearchPromptRepository repository;

    private SearchPromptAdapter adapter;

    private static final SearchPromptConfig GOOGLE_CONFIG = new SearchPromptConfig(
            "GOOGLE", "Google Broad Discovery",
            "Search for {topic} healthcare AI content.",
            "Broad discovery query for Google.", true);

    private static final SearchPromptConfig PERPLEXITY_CONFIG = new SearchPromptConfig(
            "PERPLEXITY", "Perplexity Deep Research",
            "Find substantive content on {topic} for an AI healthcare newsletter.",
            "Deep research query for Perplexity Sonar.", true);

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        adapter = new SearchPromptAdapter(repository);
    }

    @Test
    void save_and_findByEngine_roundTrips() {
        adapter.save(GOOGLE_CONFIG);

        Optional<SearchPromptConfig> found = adapter.findByEngine("GOOGLE");

        assertThat(found).isPresent();
        assertThat(found.get().engine()).isEqualTo("GOOGLE");
        assertThat(found.get().name()).isEqualTo("Google Broad Discovery");
        assertThat(found.get().templateText()).contains("{topic}");
        assertThat(found.get().active()).isTrue();
    }

    @Test
    void findByEngine_notFound_returnsEmpty() {
        Optional<SearchPromptConfig> result = adapter.findByEngine("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_empty_returnsEmptyList() {
        List<SearchPromptConfig> result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_populated_returnsAll() {
        adapter.save(GOOGLE_CONFIG);
        adapter.save(PERPLEXITY_CONFIG);

        List<SearchPromptConfig> result = adapter.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void save_existingEngine_updatesInPlace() {
        adapter.save(GOOGLE_CONFIG);

        SearchPromptConfig updated = new SearchPromptConfig(
                "GOOGLE", "Google Updated", "Updated {topic} template.", "Updated desc.", false);
        adapter.save(updated);

        Optional<SearchPromptConfig> found = adapter.findByEngine("GOOGLE");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Google Updated");
        assertThat(found.get().active()).isFalse();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void nullDescription_mappedToEmptyString() {
        SearchPromptConfig noDesc = new SearchPromptConfig(
                "GOOGLE", "Google", "Find {topic}.", null, true);
        adapter.save(noDesc);

        Optional<SearchPromptConfig> found = adapter.findByEngine("GOOGLE");
        assertThat(found).isPresent();
        assertThat(found.get().description()).isEqualTo("");
    }
}
