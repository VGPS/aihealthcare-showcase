package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.Jurisdiction;
import com.wgblackmon.aihealthcare.domain.marketanalysis.RegulatoryTracker;
import com.wgblackmon.aihealthcare.domain.marketanalysis.RulemakingStage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} integration tests for {@link RegulatoryTrackerRepositoryAdapter}.
 *
 * <p>Runs against the real Postgres test database ({@code aihealthcaredb_test}).
 * Each test runs in a rolled-back transaction.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@DataJpaTest
@Import(RegulatoryTrackerRepositoryAdapter.class)
class RegulatoryTrackerRepositoryAdapterTest {

    @Autowired
    private RegulatoryTrackerRepositoryAdapter adapter;

    private static final Instant NOW = Instant.now();

    private RegulatoryTracker tracker(String docketId, Jurisdiction j, RulemakingStage stage,
                                      LocalDate deadline) {
        return new RegulatoryTracker(j, stage, docketId,
                "Rule: " + docketId, deadline, NOW);
    }

    @Test
    void upsert_insertsNewTracker() {
        RegulatoryTracker t = tracker("FDA-001", Jurisdiction.US_FDA,
                RulemakingStage.COMMENT_PERIOD, LocalDate.of(2025, 6, 1));

        adapter.upsert(t);

        Optional<RegulatoryTracker> result = adapter.findByDocketId("FDA-001", Jurisdiction.US_FDA);
        assertThat(result).isPresent();
        assertThat(result.get().stage()).isEqualTo(RulemakingStage.COMMENT_PERIOD);
        assertThat(result.get().title()).isEqualTo("Rule: FDA-001");
    }

    @Test
    void upsert_replacesExistingTracker() {
        adapter.upsert(tracker("FDA-002", Jurisdiction.US_FDA,
                RulemakingStage.COMMENT_PERIOD, LocalDate.of(2025, 6, 1)));
        adapter.upsert(tracker("FDA-002", Jurisdiction.US_FDA,
                RulemakingStage.FINAL_GUIDANCE, null));

        Optional<RegulatoryTracker> result = adapter.findByDocketId("FDA-002", Jurisdiction.US_FDA);
        assertThat(result).isPresent();
        assertThat(result.get().stage()).isEqualTo(RulemakingStage.FINAL_GUIDANCE);
        assertThat(result.get().commentDeadline()).isNull();
    }

    @Test
    void findApproachingDeadlines_returnsOnlyWithinWindow() {
        adapter.upsert(tracker("NEAR-001", Jurisdiction.US_FDA,
                RulemakingStage.COMMENT_PERIOD, LocalDate.of(2025, 3, 1)));
        adapter.upsert(tracker("FAR-001", Jurisdiction.EU_AI_ACT,
                RulemakingStage.COMMENT_PERIOD, LocalDate.of(2026, 12, 31)));
        adapter.upsert(tracker("NULL-001", Jurisdiction.UK_MHRA,
                RulemakingStage.FINAL_GUIDANCE, null));

        List<RegulatoryTracker> approaching =
                adapter.findApproachingDeadlines(LocalDate.of(2025, 6, 30));

        assertThat(approaching).hasSize(1);
        assertThat(approaching.get(0).docketId()).isEqualTo("NEAR-001");
    }

    @Test
    void findAll_returnsAllTrackers() {
        adapter.upsert(tracker("ALL-001", Jurisdiction.US_FDA,
                RulemakingStage.DRAFT_GUIDANCE, null));
        adapter.upsert(tracker("ALL-002", Jurisdiction.EU_AI_ACT,
                RulemakingStage.ENFORCEMENT, null));

        List<RegulatoryTracker> all = adapter.findAll();
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void findByDocketId_differentJurisdictions_areDistinct() {
        adapter.upsert(tracker("SHARED-001", Jurisdiction.US_FDA,
                RulemakingStage.COMMENT_PERIOD, null));
        adapter.upsert(tracker("SHARED-001", Jurisdiction.EU_AI_ACT,
                RulemakingStage.DRAFT_GUIDANCE, null));

        Optional<RegulatoryTracker> fda = adapter.findByDocketId("SHARED-001", Jurisdiction.US_FDA);
        Optional<RegulatoryTracker> eu  = adapter.findByDocketId("SHARED-001", Jurisdiction.EU_AI_ACT);

        assertThat(fda).isPresent();
        assertThat(fda.get().stage()).isEqualTo(RulemakingStage.COMMENT_PERIOD);
        assertThat(eu).isPresent();
        assertThat(eu.get().stage()).isEqualTo(RulemakingStage.DRAFT_GUIDANCE);
    }
}
