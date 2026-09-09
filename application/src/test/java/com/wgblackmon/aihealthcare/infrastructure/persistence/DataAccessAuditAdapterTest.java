package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.DataAccessAction;
import com.wgblackmon.aihealthcare.domain.model.DataAccessAuditEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DataAccessAuditAdapter} — append-only persistence,
 * time-bounded retrieval, and owner scoping.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@DataJpaTest
class DataAccessAuditAdapterTest {

    @Autowired
    private EnterpriseDataAuditRepository repository;

    private DataAccessAuditAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DataAccessAuditAdapter(repository);
    }

    private DataAccessAuditEntry entry(String ownerEmail, DataAccessAction action, Instant at) {
        return new DataAccessAuditEntry(
                at, ownerEmail, "j1", null, action, "OK", "test detail", 10, 512L);
    }

    @Test
    void append_persists() {
        adapter.append(entry("alice@test.com", DataAccessAction.SUBMIT,
                Instant.parse("2026-09-01T12:00:00Z")));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void findByOwnerEmail_noSinceFilter_returnsAll() {
        adapter.append(entry("alice@test.com", DataAccessAction.SUBMIT,
                Instant.parse("2026-09-01T10:00:00Z")));
        adapter.append(entry("alice@test.com", DataAccessAction.COMPLETE,
                Instant.parse("2026-09-01T12:00:00Z")));

        List<DataAccessAuditEntry> results = adapter.findByOwnerEmail("alice@test.com", null, 100);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).occurredAt()).isAfter(results.get(1).occurredAt());
    }

    @Test
    void findByOwnerEmail_withSinceFilter_excludesOlderEntries() {
        adapter.append(entry("alice@test.com", DataAccessAction.SUBMIT,
                Instant.parse("2026-08-01T00:00:00Z")));
        adapter.append(entry("alice@test.com", DataAccessAction.COMPLETE,
                Instant.parse("2026-09-01T12:00:00Z")));

        Instant since = Instant.parse("2026-09-01T00:00:00Z");
        List<DataAccessAuditEntry> results = adapter.findByOwnerEmail("alice@test.com", since, 100);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).action()).isEqualTo(DataAccessAction.COMPLETE);
    }

    @Test
    void findByOwnerEmail_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            adapter.append(entry("alice@test.com", DataAccessAction.SUBMIT,
                    Instant.parse("2026-09-01T1" + i + ":00:00Z")));
        }

        List<DataAccessAuditEntry> results = adapter.findByOwnerEmail("alice@test.com", null, 3);
        assertThat(results).hasSize(3);
    }

    @Test
    void findByOwnerEmail_isolatesByOwner() {
        adapter.append(entry("alice@test.com", DataAccessAction.SUBMIT,
                Instant.parse("2026-09-01T12:00:00Z")));
        adapter.append(entry("bob@test.com", DataAccessAction.SUBMIT,
                Instant.parse("2026-09-01T12:00:00Z")));

        List<DataAccessAuditEntry> aliceEntries = adapter.findByOwnerEmail("alice@test.com", null, 100);
        assertThat(aliceEntries).hasSize(1);
        assertThat(aliceEntries.get(0).ownerEmail()).isEqualTo("alice@test.com");
    }

    @Test
    void roundTrip_preservesAllFields() {
        DataAccessAuditEntry original = new DataAccessAuditEntry(
                Instant.parse("2026-09-01T12:00:00Z"),
                "alice@test.com",
                "job-42",
                "sched-7",
                DataAccessAction.DOWNLOAD_ARTIFACT,
                "SUCCESS",
                "Downloaded 42-row CSV",
                42,
                2048L
        );
        adapter.append(original);

        DataAccessAuditEntry found = adapter.findByOwnerEmail("alice@test.com", null, 1).get(0);
        assertThat(found.occurredAt()).isEqualTo(original.occurredAt());
        assertThat(found.jobId()).isEqualTo("job-42");
        assertThat(found.scheduleId()).isEqualTo("sched-7");
        assertThat(found.action()).isEqualTo(DataAccessAction.DOWNLOAD_ARTIFACT);
        assertThat(found.outcome()).isEqualTo("SUCCESS");
        assertThat(found.detail()).isEqualTo("Downloaded 42-row CSV");
        assertThat(found.rowCount()).isEqualTo(42);
        assertThat(found.byteSize()).isEqualTo(2048L);
    }
}
