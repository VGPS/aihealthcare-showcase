package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.DataAccessAction;
import com.wgblackmon.aihealthcare.domain.model.DataAccessAuditEntry;
import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataAccessAuditPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataArtifactPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLogPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Deletes expired artifacts and log files, marks their jobs EXPIRED.
 *
 * <p>Runs daily at 03:15 UTC (configurable), deliberately before the
 * 04:00 harvest wave. For each expired job: deletes the artifact file,
 * deletes the log file, updates the job status to EXPIRED, and records
 * an audit entry. Audit rows are never deleted.
 *
 * <p>Only processes jobs whose {@code expiresAt} is in the past. Jobs
 * that are still live (not yet expired) are untouched.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class EnterpriseDataRetentionScheduler {

    private final DataJobPort dataJobPort;
    private final DataArtifactPort artifactPort;
    private final DataJobLogPort dataJobLogPort;
    private final DataAccessAuditPort auditPort;
    private final Clock clock;

    public EnterpriseDataRetentionScheduler(DataJobPort dataJobPort,
                                            DataArtifactPort artifactPort,
                                            DataJobLogPort dataJobLogPort,
                                            DataAccessAuditPort auditPort,
                                            Clock clock) {
        log.debug("EnterpriseDataRetentionScheduler() | dataJobPort={}, artifactPort={}, dataJobLogPort={}, auditPort={}",
                dataJobPort, artifactPort, dataJobLogPort, auditPort);
        this.dataJobPort = dataJobPort;
        this.artifactPort = artifactPort;
        this.dataJobLogPort = dataJobLogPort;
        this.auditPort = auditPort;
        this.clock = clock;
        log.debug("EnterpriseDataRetentionScheduler() | return=void");
    }

    @Scheduled(cron = "${aihealthcare.enterprise.data.retention-cron}")
    public void sweepExpiredJobs() {
        log.debug("sweepExpiredJobs() | now={}", Instant.now(clock));
        try {
            Instant now = Instant.now(clock);
            List<DataJob> expiredJobs = dataJobPort.findExpired(now);

            if (expiredJobs.isEmpty()) {
                log.debug("sweepExpiredJobs() | return=void (no expired jobs)");
                return;
            }

            log.info("sweepExpiredJobs() | processing {} expired job(s)", expiredJobs.size());

            for (DataJob job : expiredJobs) {
                try {
                    if (artifactPort.exists(job.jobId())) {
                        artifactPort.delete(job.jobId());
                        log.debug("sweepExpiredJobs() | deleted artifact for jobId={}", job.jobId());
                    }

                    try {
                        dataJobLogPort.delete(job.jobId());
                        log.debug("sweepExpiredJobs() | deleted log for jobId={}", job.jobId());
                    } catch (Exception logEx) {
                        log.warn("sweepExpiredJobs() | failed to delete log for jobId={}", job.jobId(), logEx);
                    }

                    dataJobPort.updateStatus(job.jobId(), DataJobStatus.EXPIRED,
                            null, null, null, null, null, null, Instant.now(clock));

                    auditPort.append(new DataAccessAuditEntry(
                            Instant.now(clock),
                            job.ownerEmail(),
                            job.jobId(),
                            job.scheduleId(),
                            DataAccessAction.COMPLETE,
                            "EXPIRED",
                            "Artifact and log deleted by retention sweeper",
                            null, null));

                    log.info("sweepExpiredJobs() | expired jobId={}, owner={}", job.jobId(), job.ownerEmail());
                } catch (Exception ex) {
                    log.error("sweepExpiredJobs() | failed to expire jobId={}", job.jobId(), ex);
                }
            }
        } catch (Exception ex) {
            log.error("sweepExpiredJobs() | unexpected error", ex);
        }
        log.debug("sweepExpiredJobs() | return=void");
    }
}
