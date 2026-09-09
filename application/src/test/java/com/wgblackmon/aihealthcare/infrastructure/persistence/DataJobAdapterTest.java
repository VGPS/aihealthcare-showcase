package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataJobMode;
import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DataJobAdapter} — round-trip persistence,
 * ownership isolation, active-count logic, stale/expired detection, and
 * partial status updates.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@DataJpaTest
class DataJobAdapterTest {

    @Autowired
    private EnterpriseDataJobRepository repository;

    private DataJobAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DataJobAdapter(repository);
    }

    private DataJob job(String jobId, String ownerEmail, DataJobStatus status) {
        return new DataJob(
                jobId, ownerEmail, null, DataJobMode.PULL, "articles",
                null, ExportFormat.CSV, status,
                null, null, null, null, null,
                null, null,
                Instant.parse("2026-09-01T12:00:00Z"), null, null,
                Instant.parse("2026-09-01T12:00:00Z"), null, null
        );
    }

    @Test
    void save_roundTrip() {
        DataJob saved = adapter.save(job("j1", "alice@test.com", DataJobStatus.QUEUED));
        assertThat(saved.jobId()).isEqualTo("j1");
        assertThat(saved.ownerEmail()).isEqualTo("alice@test.com");
        assertThat(saved.status()).isEqualTo(DataJobStatus.QUEUED);
        assertThat(saved.mode()).isEqualTo(DataJobMode.PULL);
        assertThat(saved.format()).isEqualTo(ExportFormat.CSV);
    }

    @Test
    void findByJobIdAndOwnerEmail_returnsOwnedJob() {
        adapter.save(job("j1", "alice@test.com", DataJobStatus.QUEUED));

        Optional<DataJob> found = adapter.findByJobIdAndOwnerEmail("j1", "alice@test.com");
        assertThat(found).isPresent();
        assertThat(found.get().jobId()).isEqualTo("j1");
    }

    @Test
    void findByJobIdAndOwnerEmail_crossOwnerReturnsEmpty() {
        adapter.save(job("j1", "alice@test.com", DataJobStatus.QUEUED));

        Optional<DataJob> found = adapter.findByJobIdAndOwnerEmail("j1", "bob@test.com");
        assertThat(found).isEmpty();
    }

    @Test
    void findByOwnerEmail_paginatesAndOrdersBySubmittedAtDesc() {
        adapter.save(new DataJob("j1", "alice@test.com", null, DataJobMode.PULL, "articles",
                null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                null, null, null, null, null, null, null,
                Instant.parse("2026-09-01T08:00:00Z"), null, null, null, null, null));
        adapter.save(new DataJob("j2", "alice@test.com", null, DataJobMode.PULL, "articles",
                null, ExportFormat.JSON, DataJobStatus.QUEUED,
                null, null, null, null, null, null, null,
                Instant.parse("2026-09-01T12:00:00Z"), null, null, null, null, null));

        List<DataJob> page = adapter.findByOwnerEmail("alice@test.com", 0, 10);
        assertThat(page).hasSize(2);
        assertThat(page.get(0).jobId()).isEqualTo("j2");
    }

    @Test
    void countActiveByOwnerEmail_countsOnlyQueuedAndRunning() {
        adapter.save(job("j1", "alice@test.com", DataJobStatus.QUEUED));
        adapter.save(job("j2", "alice@test.com", DataJobStatus.RUNNING));
        adapter.save(job("j3", "alice@test.com", DataJobStatus.SUCCEEDED));
        adapter.save(job("j4", "alice@test.com", DataJobStatus.FAILED));
        adapter.save(job("j5", "alice@test.com", DataJobStatus.CANCELLED));

        int active = adapter.countActiveByOwnerEmail("alice@test.com");
        assertThat(active).isEqualTo(2);
    }

    @Test
    void countActiveByOwnerEmail_excludesOtherOwners() {
        adapter.save(job("j1", "alice@test.com", DataJobStatus.QUEUED));
        adapter.save(job("j2", "bob@test.com", DataJobStatus.QUEUED));

        assertThat(adapter.countActiveByOwnerEmail("alice@test.com")).isEqualTo(1);
        assertThat(adapter.countActiveByOwnerEmail("bob@test.com")).isEqualTo(1);
    }

    @Test
    void updateStatus_setsFieldsOnExistingJob() {
        adapter.save(job("j1", "alice@test.com", DataJobStatus.RUNNING));

        Instant completedAt = Instant.parse("2026-09-01T13:00:00Z");
        adapter.updateStatus("j1", DataJobStatus.SUCCEEDED, null, null,
                42, 1024L, "sha256abc", "/artifacts/j1.csv", completedAt);

        Optional<DataJob> updated = adapter.findByJobIdAndOwnerEmail("j1", "alice@test.com");
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(DataJobStatus.SUCCEEDED);
        assertThat(updated.get().rowCount()).isEqualTo(42);
        assertThat(updated.get().byteSize()).isEqualTo(1024L);
        assertThat(updated.get().contentSha256()).isEqualTo("sha256abc");
        assertThat(updated.get().artifactPath()).isEqualTo("/artifacts/j1.csv");
        assertThat(updated.get().completedAt()).isEqualTo(completedAt);
    }

    @Test
    void findStaleRunning_returnsJobsWithOldHeartbeat() {
        Instant fresh = Instant.parse("2026-09-01T12:59:00Z");
        Instant stale = Instant.parse("2026-09-01T11:00:00Z");
        Instant cutoff = Instant.parse("2026-09-01T12:00:00Z");

        adapter.save(new DataJob("j-fresh", "alice@test.com", null, DataJobMode.PULL, "articles",
                null, ExportFormat.CSV, DataJobStatus.RUNNING,
                null, null, null, null, null, null, null,
                Instant.parse("2026-09-01T10:00:00Z"), null, null, fresh, null, null));
        adapter.save(new DataJob("j-stale", "alice@test.com", null, DataJobMode.PULL, "articles",
                null, ExportFormat.CSV, DataJobStatus.RUNNING,
                null, null, null, null, null, null, null,
                Instant.parse("2026-09-01T10:00:00Z"), null, null, stale, null, null));
        adapter.save(new DataJob("j-done", "alice@test.com", null, DataJobMode.PULL, "articles",
                null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                null, null, null, null, null, null, null,
                Instant.parse("2026-09-01T10:00:00Z"), null, null, stale, null, null));

        List<DataJob> staleJobs = adapter.findStaleRunning(cutoff);
        assertThat(staleJobs).hasSize(1);
        assertThat(staleJobs.get(0).jobId()).isEqualTo("j-stale");
    }

    @Test
    void findExpired_returnsTerminalJobsPastExpiry() {
        Instant past = Instant.parse("2026-09-01T00:00:00Z");
        Instant future = Instant.parse("2026-12-01T00:00:00Z");
        Instant now = Instant.parse("2026-09-01T12:00:00Z");

        adapter.save(new DataJob("j-expired", "alice@test.com", null, DataJobMode.PULL, "articles",
                null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                null, null, null, null, null, null, null,
                Instant.parse("2026-08-01T00:00:00Z"), null, null, null, past, null));
        adapter.save(new DataJob("j-valid", "alice@test.com", null, DataJobMode.PULL, "articles",
                null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                null, null, null, null, null, null, null,
                Instant.parse("2026-08-01T00:00:00Z"), null, null, null, future, null));

        List<DataJob> expired = adapter.findExpired(now);
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).jobId()).isEqualTo("j-expired");
    }
}
