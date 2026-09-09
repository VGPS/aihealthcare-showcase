package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.DataAccessAuditEntry;
import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataJobMode;
import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataAccessAuditPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataArtifactPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLogPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EnterpriseDataRetentionScheduler}.
 *
 * <p>Uses a fixed clock and mocked ports. Verifies expired artifacts
 * are deleted and live artifacts are untouched.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class EnterpriseDataRetentionSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-08T03:15:00Z");

    private DataJobPort dataJobPort;
    private DataArtifactPort artifactPort;
    private DataJobLogPort dataJobLogPort;
    private DataAccessAuditPort auditPort;
    private EnterpriseDataRetentionScheduler scheduler;

    @BeforeEach
    void setUp() {
        dataJobPort = mock(DataJobPort.class);
        artifactPort = mock(DataArtifactPort.class);
        dataJobLogPort = mock(DataJobLogPort.class);
        auditPort = mock(DataAccessAuditPort.class);
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        scheduler = new EnterpriseDataRetentionScheduler(dataJobPort, artifactPort,
                dataJobLogPort, auditPort, fixedClock);
    }

    @Test
    void noExpiredJobs_doesNothing() {
        when(dataJobPort.findExpired(NOW)).thenReturn(List.of());

        scheduler.sweepExpiredJobs();

        verify(artifactPort, never()).delete(any());
        verify(dataJobLogPort, never()).delete(any());
        verify(dataJobPort, never()).updateStatus(any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void expiredJob_deletesArtifactAndLog() {
        DataJob expiredJob = new DataJob(
                "exp-1", "user@test.com", null, DataJobMode.PULL,
                "articles", null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                100, 5000L, "sha256abc", "/artifacts/exp-1.csv", "/logs/exp-1.log",
                null, null,
                NOW.minusSeconds(86400 * 15), NOW.minusSeconds(86400 * 15),
                NOW.minusSeconds(86400 * 15), NOW.minusSeconds(86400 * 15),
                NOW.minusSeconds(86400), null);

        when(dataJobPort.findExpired(NOW)).thenReturn(List.of(expiredJob));
        when(artifactPort.exists("exp-1")).thenReturn(true);

        scheduler.sweepExpiredJobs();

        verify(artifactPort).delete("exp-1");
        verify(dataJobLogPort).delete("exp-1");
    }

    @Test
    void expiredJob_markedExpired() {
        DataJob expiredJob = new DataJob(
                "exp-2", "user@test.com", null, DataJobMode.PULL,
                "articles", null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                100, 5000L, "sha256abc", "/artifacts/exp-2.csv", "/logs/exp-2.log",
                null, null,
                NOW.minusSeconds(86400 * 15), NOW.minusSeconds(86400 * 15),
                NOW.minusSeconds(86400 * 15), NOW.minusSeconds(86400 * 15),
                NOW.minusSeconds(86400), null);

        when(dataJobPort.findExpired(NOW)).thenReturn(List.of(expiredJob));
        when(artifactPort.exists("exp-2")).thenReturn(true);

        scheduler.sweepExpiredJobs();

        verify(dataJobPort).updateStatus("exp-2", DataJobStatus.EXPIRED,
                null, null, null, null, null, null, NOW);
    }

    @Test
    void expiredJob_createsAuditEntry() {
        DataJob expiredJob = new DataJob(
                "exp-3", "user@test.com", null, DataJobMode.PULL,
                "articles", null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                100, 5000L, "sha256abc", "/artifacts/exp-3.csv", "/logs/exp-3.log",
                null, null,
                NOW.minusSeconds(86400 * 15), NOW.minusSeconds(86400 * 15),
                NOW.minusSeconds(86400 * 15), NOW.minusSeconds(86400 * 15),
                NOW.minusSeconds(86400), null);

        when(dataJobPort.findExpired(NOW)).thenReturn(List.of(expiredJob));
        when(artifactPort.exists("exp-3")).thenReturn(true);

        scheduler.sweepExpiredJobs();

        ArgumentCaptor<DataAccessAuditEntry> captor = ArgumentCaptor.forClass(DataAccessAuditEntry.class);
        verify(auditPort).append(captor.capture());
        DataAccessAuditEntry entry = captor.getValue();
        assertThat(entry.ownerEmail()).isEqualTo("user@test.com");
        assertThat(entry.jobId()).isEqualTo("exp-3");
        assertThat(entry.outcome()).isEqualTo("EXPIRED");
    }

    @Test
    void expiredJob_noArtifactOnDisk_skipsDeleteButStillMarksExpired() {
        DataJob expiredJob = new DataJob(
                "exp-4", "user@test.com", null, DataJobMode.PULL,
                "articles", null, ExportFormat.CSV, DataJobStatus.FAILED,
                null, null, null, null, "/logs/exp-4.log",
                "TIMEOUT", "Job timed out",
                NOW.minusSeconds(86400 * 15), NOW.minusSeconds(86400 * 15),
                NOW.minusSeconds(86400 * 15), NOW.minusSeconds(86400 * 15),
                NOW.minusSeconds(86400), null);

        when(dataJobPort.findExpired(NOW)).thenReturn(List.of(expiredJob));
        when(artifactPort.exists("exp-4")).thenReturn(false);

        scheduler.sweepExpiredJobs();

        verify(artifactPort, never()).delete("exp-4");
        verify(dataJobLogPort).delete("exp-4");
        verify(dataJobPort).updateStatus("exp-4", DataJobStatus.EXPIRED,
                null, null, null, null, null, null, NOW);
    }

    @Test
    void logDeleteFailure_doesNotPreventExpiration() {
        DataJob expiredJob = new DataJob(
                "exp-5", "user@test.com", null, DataJobMode.PULL,
                "articles", null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                100, 5000L, "sha256abc", "/artifacts/exp-5.csv", "/logs/exp-5.log",
                null, null,
                NOW.minusSeconds(86400 * 15), NOW.minusSeconds(86400 * 15),
                NOW.minusSeconds(86400 * 15), NOW.minusSeconds(86400 * 15),
                NOW.minusSeconds(86400), null);

        when(dataJobPort.findExpired(NOW)).thenReturn(List.of(expiredJob));
        when(artifactPort.exists("exp-5")).thenReturn(true);
        doThrow(new RuntimeException("log file locked")).when(dataJobLogPort).delete("exp-5");

        scheduler.sweepExpiredJobs();

        verify(artifactPort).delete("exp-5");
        verify(dataJobPort).updateStatus("exp-5", DataJobStatus.EXPIRED,
                null, null, null, null, null, null, NOW);
        verify(auditPort).append(any());
    }
}
