package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.LintReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link LintReportAdapter} using the JPA slice.
 *
 * <p>Verifies save/retrieve round-trip and pipe-delimited serialization
 * of slug lists and broken-ref entries.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
@DataJpaTest
@Import(LintReportAdapter.class)
class LintReportAdapterTest {

    @Autowired
    private LintReportAdapter adapter;

    private static final Instant STARTED = Instant.parse("2026-07-05T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-07-05T10:01:00Z");

    @Test
    void save_andFindAll_roundTrip() {
        LintReport report = new LintReport(
                STARTED, COMPLETED, 25,
                List.of("orphan-a", "orphan-b"),
                List.of("page-x -> ghost-1", "page-y -> ghost-2"),
                List.of("stale-page"),
                List.of("no-sources-page"),
                List.of("Minor formatting issue")
        );

        adapter.save(report);
        List<LintReport> all = adapter.findAll();

        assertThat(all).hasSize(1);
        LintReport loaded = all.get(0);
        assertThat(loaded.runStartedAt()).isEqualTo(STARTED);
        assertThat(loaded.runCompletedAt()).isEqualTo(COMPLETED);
        assertThat(loaded.totalPagesChecked()).isEqualTo(25);
        assertThat(loaded.orphanedSlugs()).containsExactly("orphan-a", "orphan-b");
        assertThat(loaded.brokenRefs()).containsExactly("page-x -> ghost-1", "page-y -> ghost-2");
        assertThat(loaded.staleSlugs()).containsExactly("stale-page");
        assertThat(loaded.missingProvenance()).containsExactly("no-sources-page");
        assertThat(loaded.warnings()).containsExactly("Minor formatting issue");
    }

    @Test
    void save_emptyLists_roundTrip() {
        LintReport report = new LintReport(
                STARTED, COMPLETED, 0,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );

        adapter.save(report);
        List<LintReport> all = adapter.findAll();

        assertThat(all).hasSize(1);
        LintReport loaded = all.get(0);
        assertThat(loaded.orphanedSlugs()).isEmpty();
        assertThat(loaded.brokenRefs()).isEmpty();
        assertThat(loaded.staleSlugs()).isEmpty();
        assertThat(loaded.missingProvenance()).isEmpty();
        assertThat(loaded.warnings()).isEmpty();
    }

    @Test
    void findAll_orderedByRunStartedAtDesc() {
        LintReport older = new LintReport(
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T10:01:00Z"),
                10, List.of(), List.of(), List.of(), List.of(), List.of()
        );
        LintReport newer = new LintReport(
                Instant.parse("2026-07-05T10:00:00Z"),
                Instant.parse("2026-07-05T10:01:00Z"),
                20, List.of("orphan"), List.of(), List.of(), List.of(), List.of()
        );

        adapter.save(older);
        adapter.save(newer);
        List<LintReport> all = adapter.findAll();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).totalPagesChecked()).isEqualTo(20);
        assertThat(all.get(1).totalPagesChecked()).isEqualTo(10);
    }

    @Test
    void findLatest_returnsNewest() {
        LintReport older = new LintReport(
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T10:01:00Z"),
                10, List.of(), List.of(), List.of(), List.of(), List.of()
        );
        LintReport newer = new LintReport(
                Instant.parse("2026-07-05T10:00:00Z"),
                Instant.parse("2026-07-05T10:01:00Z"),
                20, List.of("orphan"), List.of(), List.of(), List.of(), List.of()
        );

        adapter.save(older);
        adapter.save(newer);
        LintReport latest = adapter.findLatest();

        assertThat(latest).isNotNull();
        assertThat(latest.totalPagesChecked()).isEqualTo(20);
    }

    @Test
    void findLatest_returnsNull_whenEmpty() {
        LintReport latest = adapter.findLatest();

        assertThat(latest).isNull();
    }
}
