package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DealSignalAdapter} using H2 in-memory DB.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@DataJpaTest
class DealSignalAdapterTest {

    @Autowired
    private DealSignalRepository repository;

    private DealSignalAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DealSignalAdapter(repository);
    }

    private DealSignal signal(String id, String articleId, DealSignalType type) {
        return new DealSignal(id, articleId, "Title " + id, type,
                "Company", "Summary", 0.75, Instant.now(),
                null, null, null, null);
    }

    @Test
    void saveAll_persistsSignals() {
        adapter.saveAll(List.of(
                signal("s1", "a1", DealSignalType.FUNDING),
                signal("s2", "a2", DealSignalType.ACQUISITION)
        ));
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void findRecent_returnsOrderedByDetectedAtDesc() {
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        Instant later = Instant.parse("2026-06-01T00:00:00Z");

        adapter.saveAll(List.of(
                new DealSignal("s1", "a1", "Old", DealSignalType.FUNDING, "Co", "Sum", 0.5, earlier,
                        null, null, null, null),
                new DealSignal("s2", "a2", "New", DealSignalType.IPO, "Co", "Sum", 0.8, later,
                        "$100M", "Investor", "https://example.com", "Analysis")
        ));

        List<DealSignal> result = adapter.findRecent(10);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).signalId()).isEqualTo("s2");
        assertThat(result.get(1).signalId()).isEqualTo("s1");
    }

    @Test
    void findRecent_respectsLimit() {
        adapter.saveAll(List.of(
                signal("s1", "a1", DealSignalType.FUNDING),
                signal("s2", "a2", DealSignalType.IPO),
                signal("s3", "a3", DealSignalType.PARTNERSHIP)
        ));

        List<DealSignal> result = adapter.findRecent(2);
        assertThat(result).hasSize(2);
    }

    @Test
    void existsByArticleId_trueWhenExists() {
        adapter.saveAll(List.of(signal("s1", "a1", DealSignalType.FUNDING)));
        assertThat(adapter.existsByArticleId("a1")).isTrue();
    }

    @Test
    void existsByArticleId_falseWhenMissing() {
        assertThat(adapter.existsByArticleId("nonexistent")).isFalse();
    }

    @Test
    void findById_existingSignal_returnsSignal() {
        adapter.saveAll(List.of(signal("s1", "a1", DealSignalType.FUNDING)));
        DealSignal result = adapter.findById("s1");
        assertThat(result).isNotNull();
        assertThat(result.signalId()).isEqualTo("s1");
    }

    @Test
    void findById_missingSignal_returnsNull() {
        DealSignal result = adapter.findById("nonexistent");
        assertThat(result).isNull();
    }

    @Test
    void findByType_returnsFilteredSignals() {
        adapter.saveAll(List.of(
                signal("s1", "a1", DealSignalType.FUNDING),
                signal("s2", "a2", DealSignalType.ACQUISITION),
                signal("s3", "a3", DealSignalType.FUNDING)
        ));

        List<DealSignal> result = adapter.findByType("FUNDING", 10);
        assertThat(result).hasSize(2);
        for (DealSignal s : result) {
            assertThat(s.signalType()).isEqualTo(DealSignalType.FUNDING);
        }
    }

    @Test
    void saveAll_persistsOptionalFields() {
        DealSignal withFields = new DealSignal("s10", "a10", "Title", DealSignalType.FUNDING,
                "Tempus", "Summary", 0.9, Instant.now(),
                "$200M", "SoftBank", "https://example.com/art", "LLM analysis text");
        adapter.saveAll(List.of(withFields));

        DealSignal loaded = adapter.findById("s10");
        assertThat(loaded).isNotNull();
        assertThat(loaded.dealAmount()).isEqualTo("$200M");
        assertThat(loaded.counterpartyName()).isEqualTo("SoftBank");
        assertThat(loaded.sourceUrl()).isEqualTo("https://example.com/art");
        assertThat(loaded.llmAnalysis()).isEqualTo("LLM analysis text");
    }
}
