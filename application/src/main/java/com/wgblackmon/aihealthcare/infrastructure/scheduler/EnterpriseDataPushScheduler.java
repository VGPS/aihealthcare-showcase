package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.*;
import com.wgblackmon.aihealthcare.infrastructure.config.EnterpriseDataProperties;
import com.wgblackmon.aihealthcare.infrastructure.config.TierLimitProperties;
import com.wgblackmon.aihealthcare.infrastructure.enterprise.push.CronScheduleCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Per-minute sweeper that fires due push schedules.
 *
 * <p>The sweep is deliberately cheap: query for due schedules, claim each
 * one atomically, submit the data job, and return. All blocking work
 * (job execution, artifact delivery, outcome recording) runs on the
 * enterprise executor, never on the sweeper thread.
 *
 * <p>Each schedule iteration is wrapped in its own try/catch so one bad
 * schedule cannot abort the entire sweep — the same error-isolation
 * pattern used by {@code StartupPipelineOrchestrator}.
 *
 * <p>When {@code consecutiveFailures} reaches the configured threshold,
 * the schedule is deactivated and an admin notification is raised.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class EnterpriseDataPushScheduler {

    private final DataPushSchedulePort schedulePort;
    private final RequestEnterpriseDataUseCase dataService;
    private final DataPushDeliveryPort deliveryPort;
    private final DataArtifactPort artifactPort;
    private final DataJobPort dataJobPort;
    private final DataAccessAuditPort auditPort;
    private final AdminNotificationPort adminNotificationPort;
    private final CronScheduleCalculator cronCalculator;
    private final EnterpriseDataProperties dataProperties;
    private final TierLimitProperties tierLimitProperties;
    private final Clock clock;
    private final Executor executor;

    public EnterpriseDataPushScheduler(
            DataPushSchedulePort schedulePort,
            RequestEnterpriseDataUseCase dataService,
            DataPushDeliveryPort deliveryPort,
            DataArtifactPort artifactPort,
            DataJobPort dataJobPort,
            DataAccessAuditPort auditPort,
            AdminNotificationPort adminNotificationPort,
            CronScheduleCalculator cronCalculator,
            EnterpriseDataProperties dataProperties,
            TierLimitProperties tierLimitProperties,
            Clock clock,
            @Qualifier("enterpriseDataExecutor") Executor executor) {
        log.debug("EnterpriseDataPushScheduler() | constructed");
        this.schedulePort = schedulePort;
        this.dataService = dataService;
        this.deliveryPort = deliveryPort;
        this.artifactPort = artifactPort;
        this.dataJobPort = dataJobPort;
        this.auditPort = auditPort;
        this.adminNotificationPort = adminNotificationPort;
        this.cronCalculator = cronCalculator;
        this.dataProperties = dataProperties;
        this.tierLimitProperties = tierLimitProperties;
        this.clock = clock;
        this.executor = executor;
    }

    @Scheduled(cron = "${aihealthcare.enterprise.data.push.sweep-cron}")
    public void sweep() {
        log.debug("sweep() | starting");
        Instant now = clock.instant();
        int batchSize = dataProperties.getPush().getDueBatchSize();

        List<DataPushSchedule> dueSchedules = schedulePort.findDue(now, batchSize);
        if (dueSchedules.isEmpty()) {
            log.debug("sweep() | return=void (no due schedules)");
            return;
        }

        log.info("sweep() | {} due schedule(s) found", dueSchedules.size());

        for (DataPushSchedule schedule : dueSchedules) {
            try {
                processSchedule(schedule, now);
            } catch (Exception ex) {
                log.error("sweep() | error processing scheduleId={}: {}",
                        schedule.scheduleId(), ex.getMessage(), ex);
            }
        }

        log.debug("sweep() | return=void");
    }

    private void processSchedule(DataPushSchedule schedule, Instant now) {
        log.debug("processSchedule() | scheduleId={}", schedule.scheduleId());

        Instant newNextRunAt = cronCalculator.nextRunAfter(
                schedule.cronExpression(), schedule.zoneId(), now);

        boolean claimed = schedulePort.claim(
                schedule.scheduleId(), schedule.nextRunAt(), newNextRunAt, now);
        if (!claimed) {
            log.debug("processSchedule() | claim failed for scheduleId={}, skipping",
                    schedule.scheduleId());
            return;
        }

        int monthlyLimit = tierLimitProperties.getEnterprise().getMonthlyPushRuns();
        if (monthlyLimit > 0) {
            Instant startOfMonth = YearMonth.now(clock).atDay(1)
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
            int pushesThisMonth = dataJobPort.countPushRunsSince(
                    schedule.ownerEmail(), startOfMonth);
            if (pushesThisMonth >= monthlyLimit) {
                log.warn("processSchedule() | quota exceeded for scheduleId={}, pushes={}/{}",
                        schedule.scheduleId(), pushesThisMonth, monthlyLimit);
                auditPort.append(new DataAccessAuditEntry(
                        now, schedule.ownerEmail(), null, schedule.scheduleId(),
                        DataAccessAction.QUOTA_DENY, "DENY",
                        "Monthly push quota exceeded: " + pushesThisMonth + "/" + monthlyLimit,
                        null, null));
                schedulePort.recordOutcome(schedule.scheduleId(), null,
                        DataJobStatus.FAILED, now);
                return;
            }
        }

        String jobId = UUID.randomUUID().toString();
        DataRequest request = new DataRequest(
                jobId, schedule.ownerEmail(), null,
                DataJobMode.PUSH, schedule.feedId(),
                schedule.promptId(), schedule.promptText(),
                schedule.parameters(), schedule.format(),
                1000, null, null, now,
                schedule.scheduleId());

        DataJob job;
        try {
            job = dataService.submit(request);
        } catch (Exception ex) {
            log.error("processSchedule() | submit failed for scheduleId={}: {}",
                    schedule.scheduleId(), ex.getMessage());
            handleFailure(schedule, jobId, ex.getMessage(), now);
            return;
        }

        auditPort.append(new DataAccessAuditEntry(
                now, schedule.ownerEmail(), job.jobId(), schedule.scheduleId(),
                DataAccessAction.SCHEDULE_FIRE, "SUCCESS",
                "Submitted push job", null, null));

        executor.execute(() -> awaitAndDeliver(schedule, job.jobId()));

        log.debug("processSchedule() | return=void (delivery dispatched async)");
    }

    private void awaitAndDeliver(DataPushSchedule schedule, String jobId) {
        log.debug("awaitAndDeliver() | scheduleId={}, jobId={}", schedule.scheduleId(), jobId);

        try {
            DataJob job = pollUntilTerminal(jobId, schedule.ownerEmail());
            Instant now = clock.instant();

            if (job.status() == DataJobStatus.SUCCEEDED && artifactPort.exists(job.jobId())) {
                DataArtifact artifact = new DataArtifact(
                        job.jobId(), job.format(), job.artifactPath(),
                        job.byteSize() != null ? job.byteSize() : 0L,
                        job.contentSha256(), now,
                        job.expiresAt());

                PushDeliveryResult result = deliveryPort.deliver(schedule, job, artifact);

                if (result.success()) {
                    schedulePort.recordOutcome(schedule.scheduleId(), jobId,
                            DataJobStatus.SUCCEEDED, now);
                    auditPort.append(new DataAccessAuditEntry(
                            now, schedule.ownerEmail(), jobId, schedule.scheduleId(),
                            DataAccessAction.PUSH_SEND, "SUCCESS",
                            "mode=" + result.mode() + " recipients=" + result.recipients().size(),
                            job.rowCount(), job.byteSize()));
                } else {
                    handleFailure(schedule, jobId, result.errorMessage(), now);
                }
            } else {
                handleFailure(schedule, jobId,
                        "Job ended with status " + job.status(), clock.instant());
            }
        } catch (Exception ex) {
            log.error("awaitAndDeliver() | error for scheduleId={}, jobId={}",
                    schedule.scheduleId(), jobId, ex);
            handleFailure(schedule, jobId, ex.getMessage(), clock.instant());
        }

        log.debug("awaitAndDeliver() | return=void");
    }

    private DataJob pollUntilTerminal(String jobId, String ownerEmail) throws InterruptedException {
        log.debug("pollUntilTerminal() | jobId={}", jobId);
        int maxAttempts = 360;
        for (int i = 0; i < maxAttempts; i++) {
            DataJob job = dataJobPort.findByJobId(jobId).orElse(null);
            if (job != null && job.status().isTerminal()) {
                log.debug("pollUntilTerminal() | return=status={}", job.status());
                return job;
            }
            Thread.sleep(5000);
        }
        throw new IllegalStateException("Job " + jobId + " did not reach terminal state within timeout");
    }

    private void handleFailure(DataPushSchedule schedule, String jobId,
                               String errorMessage, Instant now) {
        log.warn("handleFailure() | scheduleId={}, jobId={}, error={}",
                schedule.scheduleId(), jobId, errorMessage);

        schedulePort.recordOutcome(schedule.scheduleId(), jobId,
                DataJobStatus.FAILED, now);

        auditPort.append(new DataAccessAuditEntry(
                now, schedule.ownerEmail(), jobId, schedule.scheduleId(),
                DataAccessAction.PUSH_FAIL, "FAILED", errorMessage, null, null));

        int threshold = dataProperties.getPush().getFailureThreshold();
        int newFailures = schedule.consecutiveFailures() + 1;

        if (newFailures >= threshold) {
            schedulePort.deactivate(schedule.scheduleId(),
                    "Auto-deactivated after " + newFailures + " consecutive failures");

            auditPort.append(new DataAccessAuditEntry(
                    now, schedule.ownerEmail(), null, schedule.scheduleId(),
                    DataAccessAction.SCHEDULE_DEACTIVATE, "AUTO",
                    "Threshold " + threshold + " reached", null, null));

            adminNotificationPort.notifyScheduleDeactivated(
                    schedule.scheduleId(), schedule.ownerEmail(), errorMessage);
        }
    }
}
