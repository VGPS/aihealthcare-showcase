package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataAccessAuditPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataPushSchedulePort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataPushScheduleService} — tier enforcement, ownership,
 * max-schedules cap, cron validation, nextRunAt recomputation, and runNow.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class DataPushScheduleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    private DataPushSchedulePort schedulePort;
    private DataAccessAuditPort auditPort;
    private AppUserPort appUserPort;
    private RequestEnterpriseDataUseCase dataService;
    private CronScheduleCalculator cronCalculator;
    private DataPushScheduleService service;

    @BeforeEach
    void setUp() {
        schedulePort = mock(DataPushSchedulePort.class);
        auditPort = mock(DataAccessAuditPort.class);
        appUserPort = mock(AppUserPort.class);
        dataService = mock(RequestEnterpriseDataUseCase.class);
        cronCalculator = new CronScheduleCalculator(15);
        service = new DataPushScheduleService(
                schedulePort, auditPort, appUserPort, dataService,
                cronCalculator, FIXED_CLOCK, 5);
    }

    private AppUser enterpriseUser() {
        return new AppUser("alice@test.com", "hash", "Alice", "USER",
                true, SubscriptionTier.ENTERPRISE, null);
    }

    private AppUser freeUser() {
        return new AppUser("bob@test.com", "hash", "Bob", "USER",
                true, SubscriptionTier.FREE, null);
    }

    private DataPushSchedule schedule(String cron, String zone) {
        return new DataPushSchedule(
                "sched-1", "alice@test.com", "Daily Articles", "articles",
                null, null, Map.of(), ExportFormat.CSV,
                cron, zone,
                List.of("r@test.com"), true, null,
                null, null, null, 0, NOW, NOW);
    }

    @Test
    @DisplayName("Non-ENTERPRISE tier is denied on create")
    void create_nonEnterpriseTier_denied() {
        when(appUserPort.findByEmail("bob@test.com")).thenReturn(Optional.of(freeUser()));

        DataPushSchedule s = new DataPushSchedule(
                "sched-1", "bob@test.com", "Test", "articles",
                null, null, Map.of(), ExportFormat.CSV,
                "0 0 7 * * MON-FRI", "America/Chicago",
                List.of("r@test.com"), true, null,
                null, null, null, 0, NOW, NOW);

        assertThatThrownBy(() -> service.create(s))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TIER_DENY");
    }

    @Test
    @DisplayName("Max schedules per account enforced on create")
    void create_maxSchedules_denied() {
        when(appUserPort.findByEmail("alice@test.com")).thenReturn(Optional.of(enterpriseUser()));
        when(schedulePort.findByOwnerEmail("alice@test.com"))
                .thenReturn(List.of(
                        schedule("0 0 7 * * MON-FRI", "UTC"),
                        schedule("0 0 7 * * MON-FRI", "UTC"),
                        schedule("0 0 7 * * MON-FRI", "UTC"),
                        schedule("0 0 7 * * MON-FRI", "UTC"),
                        schedule("0 0 7 * * MON-FRI", "UTC")));

        assertThatThrownBy(() -> service.create(schedule("0 0 7 * * MON-FRI", "UTC")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAX_SCHEDULES");
    }

    @Test
    @DisplayName("Invalid cron rejected with specific error")
    void create_invalidCron_rejected() {
        when(appUserPort.findByEmail("alice@test.com")).thenReturn(Optional.of(enterpriseUser()));
        when(schedulePort.findByOwnerEmail("alice@test.com")).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(schedule("bad-cron", "UTC")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cron");
    }

    @Test
    @DisplayName("Invalid zone rejected with specific error")
    void create_invalidZone_rejected() {
        when(appUserPort.findByEmail("alice@test.com")).thenReturn(Optional.of(enterpriseUser()));
        when(schedulePort.findByOwnerEmail("alice@test.com")).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(schedule("0 0 7 * * MON-FRI", "Fake/Zone")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zone");
    }

    @Test
    @DisplayName("Successful create computes nextRunAt and audits")
    void create_success_computesNextRunAt() {
        when(appUserPort.findByEmail("alice@test.com")).thenReturn(Optional.of(enterpriseUser()));
        when(schedulePort.findByOwnerEmail("alice@test.com")).thenReturn(List.of());
        when(schedulePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DataPushSchedule result = service.create(schedule("0 0 7 * * MON-FRI", "America/Chicago"));

        assertThat(result.nextRunAt()).isNotNull();
        assertThat(result.nextRunAt()).isAfter(NOW);
        assertThat(result.active()).isTrue();
        verify(auditPort).append(any(DataAccessAuditEntry.class));
    }

    @Test
    @DisplayName("Another owner's schedule is not found on update")
    void update_wrongOwner_notFound() {
        when(appUserPort.findByEmail("alice@test.com")).thenReturn(Optional.of(enterpriseUser()));
        when(schedulePort.findByScheduleIdAndOwnerEmail("sched-1", "alice@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("sched-1", "alice@test.com",
                schedule("0 0 7 * * MON-FRI", "UTC")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("nextRunAt recomputed on cron change but not on label change")
    void update_cronChange_recomputesNextRunAt() {
        when(appUserPort.findByEmail("alice@test.com")).thenReturn(Optional.of(enterpriseUser()));

        DataPushSchedule existing = new DataPushSchedule(
                "sched-1", "alice@test.com", "Old Label", "articles",
                null, null, Map.of(), ExportFormat.CSV,
                "0 0 7 * * MON-FRI", "America/Chicago",
                List.of("r@test.com"), true,
                Instant.parse("2026-09-09T12:00:00Z"),
                null, null, null, 0, NOW, NOW);

        when(schedulePort.findByScheduleIdAndOwnerEmail("sched-1", "alice@test.com"))
                .thenReturn(Optional.of(existing));
        when(schedulePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Label-only change — nextRunAt should NOT be recomputed
        DataPushSchedule labelUpdate = new DataPushSchedule(
                "sched-1", "alice@test.com", "New Label", "articles",
                null, null, Map.of(), ExportFormat.CSV,
                "0 0 7 * * MON-FRI", "America/Chicago",
                List.of("r@test.com"), true, null,
                null, null, null, 0, NOW, NOW);

        DataPushSchedule afterLabelUpdate = service.update("sched-1", "alice@test.com", labelUpdate);
        assertThat(afterLabelUpdate.nextRunAt())
                .isEqualTo(Instant.parse("2026-09-09T12:00:00Z"));

        // Cron change — nextRunAt SHOULD be recomputed
        DataPushSchedule cronUpdate = new DataPushSchedule(
                "sched-1", "alice@test.com", "Old Label", "articles",
                null, null, Map.of(), ExportFormat.CSV,
                "0 0 8 * * MON-FRI", "America/Chicago",
                List.of("r@test.com"), true, null,
                null, null, null, 0, NOW, NOW);

        DataPushSchedule afterCronUpdate = service.update("sched-1", "alice@test.com", cronUpdate);
        assertThat(afterCronUpdate.nextRunAt())
                .isNotEqualTo(Instant.parse("2026-09-09T12:00:00Z"));
    }

    @Test
    @DisplayName("runNow submits with mode=PULL and does not touch nextRunAt")
    void runNow_submitsAsPull() {
        when(appUserPort.findByEmail("alice@test.com")).thenReturn(Optional.of(enterpriseUser()));

        DataPushSchedule existing = new DataPushSchedule(
                "sched-1", "alice@test.com", "Daily", "articles",
                null, null, Map.of(), ExportFormat.CSV,
                "0 0 7 * * MON-FRI", "America/Chicago",
                List.of("r@test.com"), true,
                Instant.parse("2026-09-09T12:00:00Z"),
                null, null, null, 0, NOW, NOW);

        when(schedulePort.findByScheduleIdAndOwnerEmail("sched-1", "alice@test.com"))
                .thenReturn(Optional.of(existing));

        DataJob mockJob = new DataJob(
                "job-99", "alice@test.com", null, DataJobMode.PULL, "articles",
                null, ExportFormat.CSV, DataJobStatus.QUEUED,
                null, null, null, null, null,
                null, null, NOW, null, null, null, NOW.plusSeconds(86400), null);
        when(dataService.submit(any(DataRequest.class))).thenReturn(mockJob);

        DataJob result = service.runNow("sched-1", "alice@test.com");
        assertThat(result.jobId()).isEqualTo("job-99");

        ArgumentCaptor<DataRequest> captor = ArgumentCaptor.forClass(DataRequest.class);
        verify(dataService).submit(captor.capture());
        assertThat(captor.getValue().mode()).isEqualTo(DataJobMode.PULL);
        assertThat(captor.getValue().scheduleId()).isNull();

        verify(schedulePort, never()).save(any());
    }

    @Test
    @DisplayName("previewNextRuns returns multiple instants")
    void previewNextRuns_returnsInstants() {
        List<Instant> runs = service.previewNextRuns("0 0 7 * * MON-FRI", "America/Chicago", 3);
        assertThat(runs).hasSize(3);
        assertThat(runs.get(0)).isAfter(NOW);
        assertThat(runs.get(1)).isAfter(runs.get(0));
    }

    @Test
    @DisplayName("delete audits and removes")
    void delete_auditsAndRemoves() {
        when(appUserPort.findByEmail("alice@test.com")).thenReturn(Optional.of(enterpriseUser()));
        when(schedulePort.findByScheduleIdAndOwnerEmail("sched-1", "alice@test.com"))
                .thenReturn(Optional.of(schedule("0 0 7 * * MON-FRI", "UTC")));

        service.delete("sched-1", "alice@test.com");

        verify(schedulePort).delete("sched-1", "alice@test.com");
        verify(auditPort).append(any(DataAccessAuditEntry.class));
    }
}
