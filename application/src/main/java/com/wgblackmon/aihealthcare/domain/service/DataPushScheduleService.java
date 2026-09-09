package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageDataPushSchedulesUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataAccessAuditPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataPushSchedulePort;
import com.wgblackmon.aihealthcare.infrastructure.enterprise.push.CronScheduleCalculator;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domain service managing enterprise push schedules — CRUD, cron validation,
 * and manual "run now" triggering.
 *
 * <p>Every method enforces ENTERPRISE tier access. Ownership is resolved in
 * the query via {@code findByScheduleIdAndOwnerEmail}. Max schedules per
 * account is enforced on create. Cron expressions and time zones are validated
 * through {@link CronScheduleCalculator}. Every mutation is audit-logged.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
public class DataPushScheduleService implements ManageDataPushSchedulesUseCase {

    private final DataPushSchedulePort schedulePort;
    private final DataAccessAuditPort auditPort;
    private final AppUserPort appUserPort;
    private final RequestEnterpriseDataUseCase dataService;
    private final CronScheduleCalculator cronCalculator;
    private final Clock clock;
    private final int maxSchedulesPerAccount;

    public DataPushScheduleService(DataPushSchedulePort schedulePort,
                                   DataAccessAuditPort auditPort,
                                   AppUserPort appUserPort,
                                   RequestEnterpriseDataUseCase dataService,
                                   CronScheduleCalculator cronCalculator,
                                   Clock clock,
                                   int maxSchedulesPerAccount) {
        log.debug("DataPushScheduleService() | maxSchedulesPerAccount={}", maxSchedulesPerAccount);
        this.schedulePort = schedulePort;
        this.auditPort = auditPort;
        this.appUserPort = appUserPort;
        this.dataService = dataService;
        this.cronCalculator = cronCalculator;
        this.clock = clock;
        this.maxSchedulesPerAccount = maxSchedulesPerAccount;
    }

    @Override
    public DataPushSchedule create(DataPushSchedule schedule) {
        log.debug("create() | scheduleId={}, ownerEmail=[REDACTED], feedId={}",
                schedule.scheduleId(), schedule.feedId());

        assertEnterpriseTier(schedule.ownerEmail());
        cronCalculator.validate(schedule.cronExpression(), schedule.zoneId());

        int existing = schedulePort.findByOwnerEmail(schedule.ownerEmail()).size();
        if (existing >= maxSchedulesPerAccount) {
            throw new IllegalStateException(
                    "MAX_SCHEDULES: Account has " + existing + "/" + maxSchedulesPerAccount + " schedules");
        }

        Instant now = clock.instant();
        Instant nextRunAt = cronCalculator.nextRunAfter(
                schedule.cronExpression(), schedule.zoneId(), now);

        DataPushSchedule toSave = new DataPushSchedule(
                schedule.scheduleId(), schedule.ownerEmail(), schedule.label(),
                schedule.feedId(), schedule.promptId(), schedule.promptText(),
                schedule.parameters(), schedule.format(),
                schedule.cronExpression(), schedule.zoneId(),
                schedule.recipients(), true, nextRunAt,
                null, null, null, 0, now, now);

        DataPushSchedule saved = schedulePort.save(toSave);

        auditPort.append(new DataAccessAuditEntry(
                now, schedule.ownerEmail(), null, schedule.scheduleId(),
                DataAccessAction.SCHEDULE_CREATE, "SUCCESS",
                "label=" + schedule.label() + " cron=" + schedule.cronExpression(),
                null, null));

        log.debug("create() | return={}", saved.scheduleId());
        return saved;
    }

    @Override
    public DataPushSchedule update(String scheduleId, String ownerEmail, DataPushSchedule updated) {
        log.debug("update() | scheduleId={}, ownerEmail=[REDACTED]", scheduleId);

        assertEnterpriseTier(ownerEmail);

        DataPushSchedule existing = schedulePort.findByScheduleIdAndOwnerEmail(scheduleId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        boolean cronChanged = !existing.cronExpression().equals(updated.cronExpression())
                || !existing.zoneId().equals(updated.zoneId());

        if (cronChanged) {
            cronCalculator.validate(updated.cronExpression(), updated.zoneId());
        }

        Instant now = clock.instant();
        Instant nextRunAt = cronChanged
                ? cronCalculator.nextRunAfter(updated.cronExpression(), updated.zoneId(), now)
                : existing.nextRunAt();

        DataPushSchedule toSave = new DataPushSchedule(
                scheduleId, ownerEmail, updated.label(),
                updated.feedId(), updated.promptId(), updated.promptText(),
                updated.parameters(), updated.format(),
                updated.cronExpression(), updated.zoneId(),
                updated.recipients(), existing.active(), nextRunAt,
                existing.lastRunAt(), existing.lastStatus(), existing.lastJobId(),
                existing.consecutiveFailures(), existing.createdAt(), now);

        DataPushSchedule saved = schedulePort.save(toSave);

        auditPort.append(new DataAccessAuditEntry(
                now, ownerEmail, null, scheduleId,
                DataAccessAction.SCHEDULE_UPDATE, "SUCCESS",
                "cronChanged=" + cronChanged, null, null));

        log.debug("update() | return={}", saved.scheduleId());
        return saved;
    }

    @Override
    public void delete(String scheduleId, String ownerEmail) {
        log.debug("delete() | scheduleId={}, ownerEmail=[REDACTED]", scheduleId);

        assertEnterpriseTier(ownerEmail);

        schedulePort.findByScheduleIdAndOwnerEmail(scheduleId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        schedulePort.delete(scheduleId, ownerEmail);

        Instant now = clock.instant();
        auditPort.append(new DataAccessAuditEntry(
                now, ownerEmail, null, scheduleId,
                DataAccessAction.SCHEDULE_DELETE, "SUCCESS", null, null, null));

        log.debug("delete() | return=void");
    }

    @Override
    public List<DataPushSchedule> list(String ownerEmail) {
        log.debug("list() | ownerEmail=[REDACTED]");
        assertEnterpriseTier(ownerEmail);
        List<DataPushSchedule> result = schedulePort.findByOwnerEmail(ownerEmail);
        log.debug("list() | return={} schedules", result.size());
        return result;
    }

    @Override
    public DataJob runNow(String scheduleId, String ownerEmail) {
        log.debug("runNow() | scheduleId={}, ownerEmail=[REDACTED]", scheduleId);

        assertEnterpriseTier(ownerEmail);

        DataPushSchedule schedule = schedulePort.findByScheduleIdAndOwnerEmail(scheduleId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        DataRequest request = new DataRequest(
                UUID.randomUUID().toString(),
                ownerEmail,
                null,
                DataJobMode.PULL,
                schedule.feedId(),
                schedule.promptId(),
                schedule.promptText(),
                schedule.parameters(),
                schedule.format(),
                1000,
                null,
                null,
                clock.instant(),
                null);

        DataJob job = dataService.submit(request);

        log.debug("runNow() | return=jobId={}", job.jobId());
        return job;
    }

    @Override
    public List<Instant> previewNextRuns(String cronExpression, String zoneId, int count) {
        log.debug("previewNextRuns() | cronExpression={}, zoneId={}, count={}", cronExpression, zoneId, count);
        cronCalculator.validate(cronExpression, zoneId);
        List<Instant> result = cronCalculator.nextRuns(cronExpression, zoneId, clock.instant(), count);
        log.debug("previewNextRuns() | return={} instants", result.size());
        return result;
    }

    private void assertEnterpriseTier(String ownerEmail) {
        AppUser user = appUserPort.findByEmail(ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        if (user.tier() == null || user.tier().ordinal() < SubscriptionTier.ENTERPRISE.ordinal()) {
            throw new IllegalStateException("TIER_DENY: Enterprise tier required");
        }
    }
}
