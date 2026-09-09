package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.DataAccessAuditEntry;
import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataJobMode;
import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataAccessAuditPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EnterpriseDataJobReaper}.
 *
 * <p>Uses a fixed clock and mocked ports. Verifies stale jobs are
 * reaped and fresh jobs are untouched.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class EnterpriseDataJobReaperTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final int STALE_MINUTES = 30;

    private DataJobPort dataJobPort;
    private DataJobLogPort dataJobLogPort;
    private DataAccessAuditPort auditPort;
    private EnterpriseDataJobReaper reaper;

    @BeforeEach
    void setUp() {
        dataJobPort = mock(DataJobPort.class);
        dataJobLogPort = mock(DataJobLogPort.class);
        auditPort = mock(DataAccessAuditPort.class);
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        reaper = new EnterpriseDataJobReaper(dataJobPort, dataJobLogPort, auditPort,
                fixedClock, STALE_MINUTES);
    }

    @Test
    void noStaleJobs_doesNothing() {
        when(dataJobPort.findStaleRunning(any())).thenReturn(List.of());

        reaper.reapStaleJobs();

        verify(dataJobPort, never()).updateStatus(any(), any(), any(), any(),
                any(), any(), any(), any(), any());
        verify(auditPort, never()).append(any());
    }

    @Test
    void staleJob_markedFailedOrphaned() {
        DataJob staleJob = new DataJob(
                "stale-1", "user@test.com", null, DataJobMode.PULL,
                "articles", null, ExportFormat.CSV, DataJobStatus.RUNNING,
                null, null, null, null, null, null, null,
                NOW.minusSeconds(3600), NOW.minusSeconds(3600),
                null, NOW.minusSeconds(2400), NOW.plusSeconds(86400), null);

        when(dataJobPort.findStaleRunning(any())).thenReturn(List.of(staleJob));
        DataJobLog mockLog = mock(DataJobLog.class);
        when(dataJobLogPort.open("stale-1")).thenReturn(mockLog);

        reaper.reapStaleJobs();

        verify(dataJobPort).updateStatus("stale-1", DataJobStatus.FAILED,
                "ORPHANED", "Job reaped — heartbeat stale for >30 minutes",
                null, null, null, null, NOW);
    }

    @Test
    void staleJob_appendsLogEntry() {
        DataJob staleJob = new DataJob(
                "stale-2", "user@test.com", null, DataJobMode.PULL,
                "articles", null, ExportFormat.CSV, DataJobStatus.RUNNING,
                null, null, null, null, null, null, null,
                NOW.minusSeconds(3600), NOW.minusSeconds(3600),
                null, NOW.minusSeconds(2400), NOW.plusSeconds(86400), null);

        when(dataJobPort.findStaleRunning(any())).thenReturn(List.of(staleJob));
        DataJobLog mockLog = mock(DataJobLog.class);
        when(dataJobLogPort.open("stale-2")).thenReturn(mockLog);

        reaper.reapStaleJobs();

        verify(mockLog).error("REAPER", "Job marked ORPHANED — heartbeat stale", null);
    }

    @Test
    void staleJob_createsAuditEntry() {
        DataJob staleJob = new DataJob(
                "stale-3", "user@test.com", null, DataJobMode.PULL,
                "articles", null, ExportFormat.CSV, DataJobStatus.RUNNING,
                null, null, null, null, null, null, null,
                NOW.minusSeconds(3600), NOW.minusSeconds(3600),
                null, NOW.minusSeconds(2400), NOW.plusSeconds(86400), null);

        when(dataJobPort.findStaleRunning(any())).thenReturn(List.of(staleJob));
        DataJobLog mockLog = mock(DataJobLog.class);
        when(dataJobLogPort.open("stale-3")).thenReturn(mockLog);

        reaper.reapStaleJobs();

        ArgumentCaptor<DataAccessAuditEntry> captor = ArgumentCaptor.forClass(DataAccessAuditEntry.class);
        verify(auditPort).append(captor.capture());
        DataAccessAuditEntry entry = captor.getValue();
        assertThat(entry.ownerEmail()).isEqualTo("user@test.com");
        assertThat(entry.jobId()).isEqualTo("stale-3");
        assertThat(entry.outcome()).isEqualTo("ORPHANED");
    }

    @Test
    void staleJob_logFailureDoesNotPreventReap() {
        DataJob staleJob = new DataJob(
                "stale-4", "user@test.com", null, DataJobMode.PULL,
                "articles", null, ExportFormat.CSV, DataJobStatus.RUNNING,
                null, null, null, null, null, null, null,
                NOW.minusSeconds(3600), NOW.minusSeconds(3600),
                null, NOW.minusSeconds(2400), NOW.plusSeconds(86400), null);

        when(dataJobPort.findStaleRunning(any())).thenReturn(List.of(staleJob));
        when(dataJobLogPort.open("stale-4")).thenThrow(new RuntimeException("log file gone"));

        reaper.reapStaleJobs();

        verify(dataJobPort).updateStatus("stale-4", DataJobStatus.FAILED,
                "ORPHANED", "Job reaped — heartbeat stale for >30 minutes",
                null, null, null, null, NOW);
        verify(auditPort).append(any());
    }

    @Test
    void correctCutoffCalculation() {
        when(dataJobPort.findStaleRunning(any())).thenReturn(List.of());

        reaper.reapStaleJobs();

        Instant expectedCutoff = NOW.minusSeconds(STALE_MINUTES * 60L);
        verify(dataJobPort).findStaleRunning(expectedCutoff);
    }
}
