package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.DataAccessAction;
import com.wgblackmon.aihealthcare.domain.model.DataAccessAuditEntry;
import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataAccessAuditPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLogPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Marks stale RUNNING jobs as FAILED/ORPHANED.
 *
 * <p>Runs every 5 minutes (configurable). A job is considered stale when
 * its {@code heartbeatAt} is older than {@code stale-job-reap-minutes}.
 * This prevents ghost jobs from spinning in the UI forever after a deploy
 * or JVM crash.
 *
 * <p>For each stale job: updates status to FAILED with errorType ORPHANED,
 * appends an error entry to the job log, and records an audit trail entry.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class EnterpriseDataJobReaper {

    private final DataJobPort dataJobPort;
    private final DataJobLogPort dataJobLogPort;
    private final DataAccessAuditPort auditPort;
    private final Clock clock;
    private final int staleMinutes;

    public EnterpriseDataJobReaper(DataJobPort dataJobPort,
                                   DataJobLogPort dataJobLogPort,
                                   DataAccessAuditPort auditPort,
                                   Clock clock,
                                   @Value("${aihealthcare.enterprise.data.stale-job-reap-minutes:30}") int staleMinutes) {
        log.debug("EnterpriseDataJobReaper() | dataJobPort={}, dataJobLogPort={}, auditPort={}, staleMinutes={}",
                dataJobPort, dataJobLogPort, auditPort, staleMinutes);
        this.dataJobPort = dataJobPort;
        this.dataJobLogPort = dataJobLogPort;
        this.auditPort = auditPort;
        this.clock = clock;
        this.staleMinutes = staleMinutes;
        log.debug("EnterpriseDataJobReaper() | return=void");
    }

    @Scheduled(cron = "${aihealthcare.enterprise.data.reaper-cron}")
    public void reapStaleJobs() {
        log.debug("reapStaleJobs() | staleMinutes={}", staleMinutes);
        try {
            Instant cutoff = Instant.now(clock).minusSeconds(staleMinutes * 60L);
            List<DataJob> staleJobs = dataJobPort.findStaleRunning(cutoff);

            if (staleJobs.isEmpty()) {
                log.debug("reapStaleJobs() | return=void (no stale jobs)");
                return;
            }

            log.info("reapStaleJobs() | reaping {} stale job(s)", staleJobs.size());

            for (DataJob job : staleJobs) {
                try {
                    dataJobPort.updateStatus(job.jobId(), DataJobStatus.FAILED,
                            "ORPHANED", "Job reaped — heartbeat stale for >" + staleMinutes + " minutes",
                            null, null, null, null, Instant.now(clock));

                    try {
                        var jobLog = dataJobLogPort.open(job.jobId());
                        jobLog.error("REAPER", "Job marked ORPHANED — heartbeat stale", null);
                    } catch (Exception logEx) {
                        log.warn("reapStaleJobs() | failed to append log for job={}", job.jobId(), logEx);
                    }

                    auditPort.append(new DataAccessAuditEntry(
                            Instant.now(clock),
                            job.ownerEmail(),
                            job.jobId(),
                            job.scheduleId(),
                            DataAccessAction.FAIL,
                            "ORPHANED",
                            "Reaped stale job — heartbeat >" + staleMinutes + "m",
                            null, null));

                    log.info("reapStaleJobs() | reaped jobId={}, owner={}", job.jobId(), job.ownerEmail());
                } catch (Exception ex) {
                    log.error("reapStaleJobs() | failed to reap jobId={}", job.jobId(), ex);
                }
            }
        } catch (Exception ex) {
            log.error("reapStaleJobs() | unexpected error", ex);
        }
        log.debug("reapStaleJobs() | return=void");
    }
}
