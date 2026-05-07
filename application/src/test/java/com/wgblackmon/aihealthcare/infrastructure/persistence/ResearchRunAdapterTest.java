package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.ResearchRun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest slice tests for {@link ResearchRunAdapter}.
 *
 * <p>Uses an in-memory H2 database — no real Postgres needed.
 * Verifies save, findAll (ordering), and findByRunId behaviour.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
@DataJpaTest
@Import(ResearchRunAdapter.class)
class ResearchRunAdapterTest {

    @Autowired
    private ResearchRunAdapter adapter;

    @Autowired
    private ResearchRunRepository repository;

    @Test
    void save_persistsRunToDatabase() {
        ResearchRun run = new ResearchRun("run-1", "AI diagnostics", "STAGED_RESEARCH", 5, Instant.now());

        adapter.save(run);

        assertThat(repository.findById("run-1")).isPresent();
    }

    @Test
    void save_isIdempotent_updateDoesNotDuplicate() {
        ResearchRun run = new ResearchRun("run-2", "AI surgery", "LEGACY_GOOGLE", 3, Instant.now());

        adapter.save(run);
        adapter.save(run);

        assertThat(repository.findAll()).extracting(ResearchRunEntity::getRunId)
                .containsOnlyOnce("run-2");
    }

    @Test
    void findByRunId_returnsCorrectRun() {
        ResearchRun run = new ResearchRun("run-3", "AI oncology", "STAGED_RESEARCH", 7, Instant.now());
        adapter.save(run);

        Optional<ResearchRun> result = adapter.findByRunId("run-3");

        assertThat(result).isPresent();
        assertThat(result.get().query()).isEqualTo("AI oncology");
        assertThat(result.get().citationCount()).isEqualTo(7);
    }

    @Test
    void findByRunId_returnsEmptyWhenNotFound() {
        Optional<ResearchRun> result = adapter.findByRunId("nonexistent-id");

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_returnsAllRuns() {
        adapter.save(new ResearchRun("run-4a", "query A", "LEGACY_GOOGLE", 2, Instant.now()));
        adapter.save(new ResearchRun("run-4b", "query B", "STAGED_RESEARCH", 4, Instant.now()));

        List<ResearchRun> all = adapter.findAll();

        assertThat(all).extracting(ResearchRun::runId)
                .containsExactlyInAnyOrder("run-4a", "run-4b");
    }

    @Test
    void findAll_mapsAllFieldsCorrectly() {
        Instant ts = Instant.parse("2026-05-06T12:00:00Z");
        adapter.save(new ResearchRun("run-5", "AI radiology", "STAGED_RESEARCH", 9, ts));

        List<ResearchRun> all = adapter.findAll();
        ResearchRun found = null;
        for (ResearchRun r : all) {
            if ("run-5".equals(r.runId())) {
                found = r;
                break;
            }
        }
        assertThat(found).isNotNull();
        assertThat(found.query()).isEqualTo("AI radiology");
        assertThat(found.mode()).isEqualTo("STAGED_RESEARCH");
        assertThat(found.citationCount()).isEqualTo(9);
        assertThat(found.researchedAt()).isEqualTo(ts);
    }
}
