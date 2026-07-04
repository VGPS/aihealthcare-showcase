package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CompilationReportAdapter} using the JPA slice.
 *
 * <p>Verifies save/retrieve round-trip and pipe-delimited serialization
 * of slug lists and warnings.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@DataJpaTest
@Import(CompilationReportAdapter.class)
class CompilationReportAdapterTest {

    @Autowired
    private CompilationReportAdapter adapter;

    private static final Instant STARTED = Instant.parse("2026-07-04T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-07-04T10:05:00Z");

    @Test
    void save_andFindAll_roundTrip() {
        CompilationReport report = new CompilationReport(
                STARTED, COMPLETED, 15,
                List.of("fda-ai-guidance", "epic-ambient"),
                List.of("google-health"),
                List.of(),
                List.of("Skipped 2 malformed articles")
        );

        adapter.save(report);
        List<CompilationReport> all = adapter.findAll();

        assertThat(all).hasSize(1);
        CompilationReport loaded = all.get(0);
        assertThat(loaded.runStartedAt()).isEqualTo(STARTED);
        assertThat(loaded.runCompletedAt()).isEqualTo(COMPLETED);
        assertThat(loaded.articlesProcessed()).isEqualTo(15);
        assertThat(loaded.pagesCreated()).containsExactly("fda-ai-guidance", "epic-ambient");
        assertThat(loaded.pagesUpdated()).containsExactly("google-health");
        assertThat(loaded.warnings()).containsExactly("Skipped 2 malformed articles");
    }

    @Test
    void save_emptyLists_roundTrip() {
        CompilationReport report = new CompilationReport(
                STARTED, COMPLETED, 0,
                List.of(), List.of(), List.of(), List.of()
        );

        adapter.save(report);
        List<CompilationReport> all = adapter.findAll();

        assertThat(all).hasSize(1);
        CompilationReport loaded = all.get(0);
        assertThat(loaded.pagesCreated()).isEmpty();
        assertThat(loaded.pagesUpdated()).isEmpty();
        assertThat(loaded.warnings()).isEmpty();
    }

    @Test
    void findAll_orderedByRunStartedAtDesc() {
        CompilationReport older = new CompilationReport(
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T10:05:00Z"),
                5, List.of(), List.of(), List.of(), List.of()
        );
        CompilationReport newer = new CompilationReport(
                Instant.parse("2026-07-04T10:00:00Z"),
                Instant.parse("2026-07-04T10:05:00Z"),
                10, List.of("new-page"), List.of(), List.of(), List.of()
        );

        adapter.save(older);
        adapter.save(newer);
        List<CompilationReport> all = adapter.findAll();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).articlesProcessed()).isEqualTo(10);
        assertThat(all.get(1).articlesProcessed()).isEqualTo(5);
    }

    @Test
    void findAll_empty_returnsEmptyList() {
        List<CompilationReport> all = adapter.findAll();

        assertThat(all).isEmpty();
    }
}
