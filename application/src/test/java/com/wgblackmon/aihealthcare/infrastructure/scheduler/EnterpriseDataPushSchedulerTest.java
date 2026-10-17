package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.*;
import com.wgblackmon.aihealthcare.infrastructure.config.EnterpriseDataProperties;
import com.wgblackmon.aihealthcare.infrastructure.config.TierLimitProperties;
import com.wgblackmon.aihealthcare.infrastructure.enterprise.push.CronScheduleCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EnterpriseDataPushScheduler} — claim semantics,
 * quota enforcement, error isolation, and deactivation threshold.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class EnterpriseDataPushSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    private DataPushSchedulePort schedulePort;
    private RequestEnterpriseDataUseCase dataService;
    private DataPushDeliveryPort deliveryPort;
    private DataArtifactPort artifactPort;
    private DataJobPort dataJobPort;
    private DataAccessAuditPort auditPort;
    private AdminNotificationPort adminNotificationPort;
    private PipelinePausePort pausePort;
    private PushApprovalGatePort approvalGatePort;
    private PushApprovalPort approvalPort;
    private EnterpriseDataProperties dataProperties;
    private TierLimitProperties tierLimitProperties;
    private EnterpriseDataPushScheduler scheduler;

    @BeforeEach
    void setUp() {
        schedulePort = mock(DataPushSchedulePort.class);
        dataService = mock(RequestEnterpriseDataUseCase.class);
        deliveryPort = mock(DataPushDeliveryPort.class);
        artifactPort = mock(DataArtifactPort.class);
        dataJobPort = mock(DataJobPort.class);
        auditPort = mock(DataAccessAuditPort.class);
        adminNotificationPort = mock(AdminNotificationPort.class);
        pausePort = mock(PipelinePausePort.class);
        when(pausePort.isPaused("enterprise-push")).thenReturn(false);
        approvalGatePort = mock(PushApprovalGatePort.class);
        when(approvalGatePort.isRequired(any())).thenReturn(false);
        approvalPort = mock(PushApprovalPort.class);
        CronScheduleCalculator cronCalculator = new CronScheduleCalculator(15);

        dataProperties = new EnterpriseDataProperties(null);
        dataProperties.setPush(new EnterpriseDataProperties.Push());
        dataProperties.getPush().setDueBatchSize(50);
        dataProperties.getPush().setFailureThreshold(3);

        tierLimitProperties = mock(TierLimitProperties.class);
        TierLimitProperties.TierConfig enterpriseTier = new TierLimitProperties.TierConfig();
        enterpriseTier.setMonthlyPushRuns(300);
        when(tierLimitProperties.getEnterprise()).thenReturn(enterpriseTier);

        Executor syncExecutor = Runnable::run;

        scheduler = new EnterpriseDataPushScheduler(
                schedulePort, dataService, deliveryPort, artifactPort,
                dataJobPort, auditPort, adminNotificationPort, pausePort,
                approvalGatePort, approvalPort,
                cronCalculator, dataProperties, tierLimitProperties,
                FIXED_CLOCK, syncExecutor);
    }

    private DataPushSchedule dueSchedule(String id, int failures) {
        return new DataPushSchedule(
                id, "alice@test.com", "Daily Export", "articles",
                null, null, Map.of(), ExportFormat.CSV,
                "0 0 7 * * MON-FRI", "America/Chicago",
                List.of("r@test.com"), true,
                NOW.minusSeconds(60),
                null, null, null, failures, NOW, NOW);
    }

    private DataJob succeededJob(String jobId) {
        return new DataJob(
                jobId, "alice@test.com", null, DataJobMode.PUSH, "articles",
                null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                100, 2048L, "sha256", jobId + ".csv", null,
                null, null, NOW, NOW, NOW, NOW, NOW.plusSeconds(86400), "sched-1");
    }

    @Test
    @DisplayName("Due schedule is claimed and submitted exactly once")
    void dueSchedule_claimedAndSubmitted() {
        DataPushSchedule schedule = dueSchedule("sched-1", 0);
        when(schedulePort.findDue(any(), eq(50))).thenReturn(List.of(schedule));
        when(schedulePort.claim(eq("sched-1"), any(), any(), any())).thenReturn(true);
        when(dataJobPort.countPushRunsSince(any(), any())).thenReturn(0);

        DataJob job = succeededJob("job-1");
        when(dataService.submit(any())).thenReturn(job);
        when(dataJobPort.findByJobId("job-1")).thenReturn(Optional.of(job));
        when(artifactPort.exists("job-1")).thenReturn(true);
        when(deliveryPort.deliver(any(), any(), any())).thenReturn(
                new PushDeliveryResult("sched-1", "job-1", PushDeliveryMode.ATTACHMENT,
                        List.of("r@test.com"), 2048L, NOW, true, null));

        scheduler.sweep();

        verify(dataService, times(1)).submit(any());
        verify(deliveryPort, times(1)).deliver(any(), any(), any());
        verify(schedulePort).recordOutcome(eq("sched-1"), eq("job-1"),
                eq(DataJobStatus.SUCCEEDED), any());
    }

    @Test
    @DisplayName("Failed claim results in no submission")
    void failedClaim_noSubmission() {
        DataPushSchedule schedule = dueSchedule("sched-1", 0);
        when(schedulePort.findDue(any(), eq(50))).thenReturn(List.of(schedule));
        when(schedulePort.claim(eq("sched-1"), any(), any(), any())).thenReturn(false);

        scheduler.sweep();

        verify(dataService, never()).submit(any());
    }

    @Test
    @DisplayName("Exception on schedule A does not prevent schedule B")
    void errorIsolation_continuesToNextSchedule() {
        DataPushSchedule schedA = dueSchedule("sched-A", 0);
        DataPushSchedule schedB = dueSchedule("sched-B", 0);
        when(schedulePort.findDue(any(), eq(50))).thenReturn(List.of(schedA, schedB));
        when(schedulePort.claim(eq("sched-A"), any(), any(), any())).thenReturn(true);
        when(schedulePort.claim(eq("sched-B"), any(), any(), any())).thenReturn(true);
        when(dataJobPort.countPushRunsSince(any(), any())).thenReturn(0);

        when(dataService.submit(argThat(r -> r != null && "sched-A".equals(r.scheduleId()))))
                .thenThrow(new RuntimeException("Boom on A"));

        DataJob jobB = succeededJob("job-B");
        when(dataService.submit(argThat(r -> r != null && "sched-B".equals(r.scheduleId()))))
                .thenReturn(jobB);
        when(dataJobPort.findByJobId("job-B")).thenReturn(Optional.of(jobB));
        when(artifactPort.exists("job-B")).thenReturn(true);
        when(deliveryPort.deliver(any(), any(), any())).thenReturn(
                new PushDeliveryResult("sched-B", "job-B", PushDeliveryMode.ATTACHMENT,
                        List.of("r@test.com"), 2048L, NOW, true, null));

        scheduler.sweep();

        verify(deliveryPort, times(1)).deliver(any(), any(), any());
    }

    @Test
    @DisplayName("Quota exceeded — no submission, outcome recorded")
    void quotaExceeded_noSubmission() {
        DataPushSchedule schedule = dueSchedule("sched-1", 0);
        when(schedulePort.findDue(any(), eq(50))).thenReturn(List.of(schedule));
        when(schedulePort.claim(eq("sched-1"), any(), any(), any())).thenReturn(true);
        when(dataJobPort.countPushRunsSince(any(), any())).thenReturn(300);

        scheduler.sweep();

        verify(dataService, never()).submit(any());
        verify(schedulePort).recordOutcome(eq("sched-1"), isNull(),
                eq(DataJobStatus.FAILED), any());

        ArgumentCaptor<DataAccessAuditEntry> captor = ArgumentCaptor.forClass(DataAccessAuditEntry.class);
        verify(auditPort).append(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(DataAccessAction.QUOTA_DENY);
    }

    @Test
    @DisplayName("Reaching failure threshold deactivates and notifies exactly once")
    void failureThreshold_deactivatesAndNotifies() {
        DataPushSchedule schedule = dueSchedule("sched-1", 2);
        when(schedulePort.findDue(any(), eq(50))).thenReturn(List.of(schedule));
        when(schedulePort.claim(eq("sched-1"), any(), any(), any())).thenReturn(true);
        when(dataJobPort.countPushRunsSince(any(), any())).thenReturn(0);
        when(dataService.submit(any())).thenThrow(new RuntimeException("Feed unavailable"));

        scheduler.sweep();

        verify(schedulePort).deactivate(eq("sched-1"), contains("consecutive failures"));
        verify(adminNotificationPort, times(1)).notifyScheduleDeactivated(
                eq("sched-1"), eq("alice@test.com"), eq("Feed unavailable"));
    }

    @Test
    @DisplayName("Paused kill switch skips the sweep entirely")
    void paused_skipsSweepEntirely() {
        when(pausePort.isPaused("enterprise-push")).thenReturn(true);

        scheduler.sweep();

        verify(schedulePort, never()).findDue(any(), anyInt());
        verify(dataService, never()).submit(any());
    }

    @Test
    @DisplayName("Approval gate required — delivery held, not sent, request saved PENDING")
    void approvalGateRequired_holdsDeliveryInsteadOfSending() {
        DataPushSchedule schedule = dueSchedule("sched-1", 0);
        when(schedulePort.findDue(any(), eq(50))).thenReturn(List.of(schedule));
        when(schedulePort.claim(eq("sched-1"), any(), any(), any())).thenReturn(true);
        when(dataJobPort.countPushRunsSince(any(), any())).thenReturn(0);

        DataJob job = succeededJob("job-1");
        when(dataService.submit(any())).thenReturn(job);
        when(dataJobPort.findByJobId("job-1")).thenReturn(Optional.of(job));
        when(artifactPort.exists("job-1")).thenReturn(true);
        when(approvalGatePort.isRequired("sched-1")).thenReturn(true);

        scheduler.sweep();

        verify(deliveryPort, never()).deliver(any(), any(), any());
        verify(schedulePort, never()).recordOutcome(any(), any(), any(), any());

        ArgumentCaptor<PushApprovalRequest> captor = ArgumentCaptor.forClass(PushApprovalRequest.class);
        verify(approvalPort).save(captor.capture());
        assertThat(captor.getValue().jobId()).isEqualTo("job-1");
        assertThat(captor.getValue().scheduleId()).isEqualTo("sched-1");
        assertThat(captor.getValue().status()).isEqualTo(PushApprovalStatus.PENDING);

        ArgumentCaptor<DataAccessAuditEntry> auditCaptor = ArgumentCaptor.forClass(DataAccessAuditEntry.class);
        verify(auditPort, atLeastOnce()).append(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues()).extracting(DataAccessAuditEntry::action)
                .contains(DataAccessAction.PUSH_HOLD);
    }

    @Test
    @DisplayName("Approval gate not required — delivers immediately as before")
    void approvalGateNotRequired_deliversImmediately() {
        DataPushSchedule schedule = dueSchedule("sched-1", 0);
        when(schedulePort.findDue(any(), eq(50))).thenReturn(List.of(schedule));
        when(schedulePort.claim(eq("sched-1"), any(), any(), any())).thenReturn(true);
        when(dataJobPort.countPushRunsSince(any(), any())).thenReturn(0);

        DataJob job = succeededJob("job-1");
        when(dataService.submit(any())).thenReturn(job);
        when(dataJobPort.findByJobId("job-1")).thenReturn(Optional.of(job));
        when(artifactPort.exists("job-1")).thenReturn(true);
        when(approvalGatePort.isRequired("sched-1")).thenReturn(false);
        when(deliveryPort.deliver(any(), any(), any())).thenReturn(
                new PushDeliveryResult("sched-1", "job-1", PushDeliveryMode.ATTACHMENT,
                        List.of("r@test.com"), 2048L, NOW, true, null));

        scheduler.sweep();

        verify(deliveryPort, times(1)).deliver(any(), any(), any());
        verify(approvalPort, never()).save(any());
    }

    @Test
    @DisplayName("nextRunAt is advanced before submission")
    void nextRunAtAdvanced_beforeSubmission() {
        DataPushSchedule schedule = dueSchedule("sched-1", 0);
        when(schedulePort.findDue(any(), eq(50))).thenReturn(List.of(schedule));
        when(schedulePort.claim(eq("sched-1"), any(), any(), any())).thenReturn(true);
        when(dataJobPort.countPushRunsSince(any(), any())).thenReturn(0);

        DataJob job = succeededJob("job-1");
        when(dataService.submit(any())).thenReturn(job);
        when(dataJobPort.findByJobId("job-1")).thenReturn(Optional.of(job));
        when(artifactPort.exists("job-1")).thenReturn(true);
        when(deliveryPort.deliver(any(), any(), any())).thenReturn(
                new PushDeliveryResult("sched-1", "job-1", PushDeliveryMode.ATTACHMENT,
                        List.of("r@test.com"), 2048L, NOW, true, null));

        scheduler.sweep();

        ArgumentCaptor<Instant> newNextCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(schedulePort).claim(eq("sched-1"), any(), newNextCaptor.capture(), any());
        assertThat(newNextCaptor.getValue()).isAfter(NOW);
    }
}
