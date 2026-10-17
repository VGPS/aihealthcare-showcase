package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.DataAccessAction;
import com.wgblackmon.aihealthcare.domain.model.DataAccessAuditEntry;
import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataJobMode;
import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.model.DataPushSchedule;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.model.PushApprovalRequest;
import com.wgblackmon.aihealthcare.domain.model.PushApprovalStatus;
import com.wgblackmon.aihealthcare.domain.model.PushDeliveryMode;
import com.wgblackmon.aihealthcare.domain.model.PushDeliveryResult;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataAccessAuditPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataArtifactPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataPushDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataPushSchedulePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PushApprovalPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PushApprovalService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@ExtendWith(MockitoExtension.class)
class PushApprovalServiceTest {

    @Mock
    private PushApprovalPort approvalPort;

    @Mock
    private DataJobPort dataJobPort;

    @Mock
    private DataArtifactPort artifactPort;

    @Mock
    private DataPushSchedulePort schedulePort;

    @Mock
    private DataPushDeliveryPort deliveryPort;

    @Mock
    private DataAccessAuditPort auditPort;

    private PushApprovalService service;

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @BeforeEach
    void setUp() {
        service = new PushApprovalService(approvalPort, dataJobPort, artifactPort,
                schedulePort, deliveryPort, auditPort);
    }

    private PushApprovalRequest pending() {
        return new PushApprovalRequest("job-1", "sched-1", "alice@test.com",
                NOW, PushApprovalStatus.PENDING, null, null);
    }

    private DataJob succeededJob() {
        return new DataJob("job-1", "alice@test.com", null, DataJobMode.PUSH, "articles",
                null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                100, 2048L, "sha256", "job-1.csv", null,
                null, null, NOW, NOW, NOW, NOW, NOW.plusSeconds(86400), "sched-1");
    }

    private DataPushSchedule schedule() {
        return new DataPushSchedule("sched-1", "alice@test.com", "Daily Export", "articles",
                null, null, Map.of(), ExportFormat.CSV,
                "0 0 7 * * MON-FRI", "America/Chicago",
                List.of("r@test.com"), true, NOW, null, null, null, 0, NOW, NOW);
    }

    @Test
    void listPending_delegatesToPort() {
        when(approvalPort.findPending()).thenReturn(List.of(pending()));

        List<PushApprovalRequest> result = service.listPending();

        assertThat(result).hasSize(1);
    }

    @Test
    void approve_deliversAndRecordsSuccess() {
        when(approvalPort.findByJobId("job-1")).thenReturn(Optional.of(pending()));
        when(dataJobPort.findByJobId("job-1")).thenReturn(Optional.of(succeededJob()));
        when(schedulePort.findByScheduleIdAndOwnerEmail("sched-1", "alice@test.com"))
                .thenReturn(Optional.of(schedule()));
        when(artifactPort.exists("job-1")).thenReturn(true);
        when(deliveryPort.deliver(any(), any(), any())).thenReturn(
                new PushDeliveryResult("sched-1", "job-1", PushDeliveryMode.ATTACHMENT,
                        List.of("r@test.com"), 2048L, NOW, true, null));

        service.approve("job-1", "admin@test.com");

        verify(deliveryPort).deliver(any(), any(), any());
        verify(schedulePort).recordOutcome(eq("sched-1"), eq("job-1"), eq(DataJobStatus.SUCCEEDED), any());

        ArgumentCaptor<PushApprovalRequest> captor = ArgumentCaptor.forClass(PushApprovalRequest.class);
        verify(approvalPort).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(PushApprovalStatus.APPROVED);
        assertThat(captor.getValue().decidedBy()).isEqualTo("admin@test.com");

        ArgumentCaptor<DataAccessAuditEntry> auditCaptor = ArgumentCaptor.forClass(DataAccessAuditEntry.class);
        verify(auditPort).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo(DataAccessAction.PUSH_SEND);
    }

    @Test
    void approve_deliveryFails_recordsFailure() {
        when(approvalPort.findByJobId("job-1")).thenReturn(Optional.of(pending()));
        when(dataJobPort.findByJobId("job-1")).thenReturn(Optional.of(succeededJob()));
        when(schedulePort.findByScheduleIdAndOwnerEmail("sched-1", "alice@test.com"))
                .thenReturn(Optional.of(schedule()));
        when(artifactPort.exists("job-1")).thenReturn(true);
        when(deliveryPort.deliver(any(), any(), any())).thenReturn(
                new PushDeliveryResult("sched-1", "job-1", PushDeliveryMode.ATTACHMENT,
                        List.of("r@test.com"), 0L, NOW, false, "SMTP timeout"));

        service.approve("job-1", "admin@test.com");

        verify(schedulePort).recordOutcome(eq("sched-1"), eq("job-1"), eq(DataJobStatus.FAILED), any());
    }

    @Test
    void approve_artifactNoLongerExists_rejectsInstead() {
        when(approvalPort.findByJobId("job-1")).thenReturn(Optional.of(pending()));
        when(dataJobPort.findByJobId("job-1")).thenReturn(Optional.of(succeededJob()));
        when(schedulePort.findByScheduleIdAndOwnerEmail("sched-1", "alice@test.com"))
                .thenReturn(Optional.of(schedule()));
        when(artifactPort.exists("job-1")).thenReturn(false);

        service.approve("job-1", "admin@test.com");

        verify(deliveryPort, never()).deliver(any(), any(), any());

        ArgumentCaptor<PushApprovalRequest> captor = ArgumentCaptor.forClass(PushApprovalRequest.class);
        verify(approvalPort).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(PushApprovalStatus.REJECTED);
    }

    @Test
    void approve_noPendingRequest_throws() {
        when(approvalPort.findByJobId("job-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve("job-1", "admin@test.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approve_alreadyDecided_throws() {
        PushApprovalRequest decided = new PushApprovalRequest("job-1", "sched-1", "alice@test.com",
                NOW, PushApprovalStatus.APPROVED, NOW, "someone@test.com");
        when(approvalPort.findByJobId("job-1")).thenReturn(Optional.of(decided));

        assertThatThrownBy(() -> service.approve("job-1", "admin@test.com"))
                .isInstanceOf(IllegalStateException.class);

        verify(deliveryPort, never()).deliver(any(), any(), any());
    }

    @Test
    void reject_marksRejected_neverDelivers() {
        when(approvalPort.findByJobId("job-1")).thenReturn(Optional.of(pending()));

        service.reject("job-1", "admin@test.com");

        verify(deliveryPort, never()).deliver(any(), any(), any());
        verify(schedulePort, never()).recordOutcome(any(), any(), any(), any());

        ArgumentCaptor<PushApprovalRequest> captor = ArgumentCaptor.forClass(PushApprovalRequest.class);
        verify(approvalPort).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(PushApprovalStatus.REJECTED);

        ArgumentCaptor<DataAccessAuditEntry> auditCaptor = ArgumentCaptor.forClass(DataAccessAuditEntry.class);
        verify(auditPort).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo(DataAccessAction.PUSH_REJECT);
    }
}
