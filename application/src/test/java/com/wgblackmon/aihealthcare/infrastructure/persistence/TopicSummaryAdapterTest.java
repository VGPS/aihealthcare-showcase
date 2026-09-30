package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.TopicSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice tests for {@link TopicSummaryAdapter}.
 *
 * <p>Uses {@code @DataJpaTest} which loads only the JPA slice (H2, repositories,
 * entity scanning) without the full Spring context.  The adapter is constructed
 * directly in {@link #setUp()} via constructor injection.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-21
 * @updated 2026-09-29
 */
@DataJpaTest
class TopicSummaryAdapterTest {

    @Autowired
    private TopicSummaryRepository repository;

    private TopicSummaryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TopicSummaryAdapter(repository);
        repository.deleteAll();
    }

    @Test
    @DisplayName("save persists and findByTopic returns the summary")
    void save_persistsAndFindByTopicReturns() {
        TopicSummary summary = new TopicSummary(
                "AI Healthcare", "This is a summary. Second sentence. Third sentence.",
                Instant.parse("2026-05-21T10:00:00Z"), null, null);

        adapter.save(summary);

        Optional<TopicSummary> result = adapter.findByTopic("AI Healthcare");
        assertThat(result).isPresent();
        assertThat(result.get().topic()).isEqualTo("AI Healthcare");
        assertThat(result.get().summaryText()).contains("This is a summary");
        assertThat(result.get().generatedAt()).isEqualTo(Instant.parse("2026-05-21T10:00:00Z"));
    }

    @Test
    @DisplayName("save with same topic overwrites previous summary (upsert)")
    void save_upsertOverwritesPreviousSummary() {
        TopicSummary first = new TopicSummary(
                "OpenAI Healthcare", "First summary.", Instant.parse("2026-05-20T10:00:00Z"), null, null);
        TopicSummary second = new TopicSummary(
                "OpenAI Healthcare", "Updated summary.", Instant.parse("2026-05-21T10:00:00Z"), null, null);

        adapter.save(first);
        adapter.save(second);

        Optional<TopicSummary> result = adapter.findByTopic("OpenAI Healthcare");
        assertThat(result).isPresent();
        assertThat(result.get().summaryText()).isEqualTo("Updated summary.");
        assertThat(result.get().generatedAt()).isEqualTo(Instant.parse("2026-05-21T10:00:00Z"));

        // Only one row should exist
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByTopic returns empty for unknown topic")
    void findByTopic_returnsEmptyForUnknownTopic() {
        Optional<TopicSummary> result = adapter.findByTopic("Nonexistent Topic");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAll returns all saved summaries")
    void findAll_returnsAllSavedSummaries() {
        adapter.save(new TopicSummary("Topic A", "Summary A.", Instant.now(), null, null));
        adapter.save(new TopicSummary("Topic B", "Summary B.", Instant.now(), null, null));
        adapter.save(new TopicSummary("Topic C", "Summary C.", Instant.now(), null, null));

        List<TopicSummary> result = adapter.findAll();
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("pipeline metadata round-trips through entity")
    void save_pipelineMetadataRoundTrips() {
        TopicSummary summary = new TopicSummary(
                "Linted Topic",
                "FDA cut prior-auth turnaround from 5 to 2 days after AI deployment [S1].",
                Instant.now(),
                "anti-slop-v1",
                87);

        adapter.save(summary);

        Optional<TopicSummary> result = adapter.findByTopic("Linted Topic");
        assertThat(result).isPresent();
        assertThat(result.get().pipelineVersion()).isEqualTo("anti-slop-v1");
        assertThat(result.get().lintScore()).isEqualTo(87);
        assertThat(result.get().isAntiSlop()).isTrue();
    }

    @Test
    @DisplayName("legacy summary with null pipeline fields round-trips correctly")
    void save_legacySummaryNullFieldsRoundTrip() {
        TopicSummary summary = new TopicSummary(
                "Legacy Topic", "AI is transforming healthcare.", Instant.now(), null, null);

        adapter.save(summary);

        Optional<TopicSummary> result = adapter.findByTopic("Legacy Topic");
        assertThat(result).isPresent();
        assertThat(result.get().pipelineVersion()).isNull();
        assertThat(result.get().lintScore()).isNull();
        assertThat(result.get().isAntiSlop()).isFalse();
    }
}
