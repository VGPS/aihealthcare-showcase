package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataJobMode;
import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link DataJobPort}.
 *
 * <p>Maps between the {@link DataJob} domain record and
 * {@link EnterpriseDataJobEntity}. Enum values are stored as
 * their {@code name()} strings and reconstituted with
 * {@code valueOf()}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class DataJobAdapter implements DataJobPort {

    private final EnterpriseDataJobRepository repository;

    public DataJobAdapter(EnterpriseDataJobRepository repository) {
        this.repository = repository;
        log.debug("DataJobAdapter() | repository={}", repository.getClass().getSimpleName());
    }

    @Override
    @Transactional
    public DataJob save(DataJob job) {
        log.debug("save() | jobId={}", job.jobId());
        EnterpriseDataJobEntity entity = toEntity(job);
        EnterpriseDataJobEntity saved = repository.save(entity);
        DataJob result = toDomain(saved);
        log.debug("save() | return={}", result.jobId());
        return result;
    }

    @Override
    public Optional<DataJob> findByJobIdAndOwnerEmail(String jobId, String ownerEmail) {
        log.debug("findByJobIdAndOwnerEmail() | jobId={}, ownerEmail={}", jobId, ownerEmail);
        Optional<DataJob> result = repository.findByJobIdAndOwnerEmail(jobId, ownerEmail)
                .map(this::toDomain);
        log.debug("findByJobIdAndOwnerEmail() | return={}", result.isPresent());
        return result;
    }

    @Override
    public List<DataJob> findByOwnerEmail(String ownerEmail, int page, int size) {
        log.debug("findByOwnerEmail() | ownerEmail={}, page={}, size={}", ownerEmail, page, size);
        List<DataJob> result = repository
                .findByOwnerEmailOrderBySubmittedAtDesc(ownerEmail, PageRequest.of(page, size))
                .map(this::toDomain)
                .getContent();
        log.debug("findByOwnerEmail() | return={} jobs", result.size());
        return result;
    }

    @Override
    public int countActiveByOwnerEmail(String ownerEmail) {
        log.debug("countActiveByOwnerEmail() | ownerEmail={}", ownerEmail);
        long count = repository.countByOwnerEmailAndStatusIn(
                ownerEmail, List.of(DataJobStatus.QUEUED.name(), DataJobStatus.RUNNING.name()));
        int result = (int) count;
        log.debug("countActiveByOwnerEmail() | return={}", result);
        return result;
    }

    @Override
    @Transactional
    public void updateStatus(String jobId, DataJobStatus status, String errorType,
                             String errorMessage, Integer rowCount, Long byteSize,
                             String contentSha256, String artifactPath, Instant completedAt) {
        log.debug("updateStatus() | jobId={}, status={}", jobId, status);
        EnterpriseDataJobEntity entity = repository.findById(jobId).orElse(null);
        if (entity == null) {
            log.warn("updateStatus() | jobId={} not found, skipping", jobId);
            return;
        }
        entity.setStatus(status.name());
        entity.setErrorType(errorType);
        entity.setErrorMessage(errorMessage);
        entity.setRowCount(rowCount);
        entity.setByteSize(byteSize);
        entity.setContentSha256(contentSha256);
        entity.setArtifactPath(artifactPath);
        entity.setCompletedAt(completedAt);
        repository.save(entity);
        log.debug("updateStatus() | return=void");
    }

    @Override
    @Transactional
    public void touchHeartbeat(String jobId, Instant heartbeatAt) {
        log.debug("touchHeartbeat() | jobId={}, heartbeatAt={}", jobId, heartbeatAt);
        repository.touchHeartbeat(jobId, heartbeatAt);
        log.debug("touchHeartbeat() | return=void");
    }

    @Override
    public List<DataJob> findStaleRunning(Instant heartbeatBefore) {
        log.debug("findStaleRunning() | heartbeatBefore={}", heartbeatBefore);
        List<DataJob> result = repository
                .findByStatusAndHeartbeatAtBefore(DataJobStatus.RUNNING.name(), heartbeatBefore)
                .stream().map(this::toDomain).toList();
        log.debug("findStaleRunning() | return={} jobs", result.size());
        return result;
    }

    @Override
    public List<DataJob> findExpired(Instant now) {
        log.debug("findExpired() | now={}", now);
        List<String> terminalStatuses = List.of(
                DataJobStatus.SUCCEEDED.name(), DataJobStatus.FAILED.name(),
                DataJobStatus.CANCELLED.name(), DataJobStatus.EXPIRED.name());
        List<DataJob> result = repository
                .findByStatusInAndExpiresAtBefore(terminalStatuses, now)
                .stream().map(this::toDomain).toList();
        log.debug("findExpired() | return={} jobs", result.size());
        return result;
    }

    // ---- mapping helpers ----

    private EnterpriseDataJobEntity toEntity(DataJob job) {
        EnterpriseDataJobEntity e = new EnterpriseDataJobEntity();
        e.setJobId(job.jobId());
        e.setOwnerEmail(job.ownerEmail());
        e.setTeamId(job.teamId());
        e.setMode(job.mode().name());
        e.setFeedId(job.feedId());
        e.setPromptId(job.promptId());
        e.setFormat(job.format().name());
        e.setStatus(job.status().name());
        e.setRowCount(job.rowCount());
        e.setByteSize(job.byteSize());
        e.setContentSha256(job.contentSha256());
        e.setArtifactPath(job.artifactPath());
        e.setLogPath(job.logPath());
        e.setErrorType(job.errorType());
        e.setErrorMessage(job.errorMessage());
        e.setSubmittedAt(job.submittedAt());
        e.setStartedAt(job.startedAt());
        e.setCompletedAt(job.completedAt());
        e.setHeartbeatAt(job.heartbeatAt());
        e.setExpiresAt(job.expiresAt());
        e.setScheduleId(job.scheduleId());
        return e;
    }

    private DataJob toDomain(EnterpriseDataJobEntity e) {
        return new DataJob(
                e.getJobId(),
                e.getOwnerEmail(),
                e.getTeamId(),
                DataJobMode.valueOf(e.getMode()),
                e.getFeedId(),
                e.getPromptId(),
                ExportFormat.valueOf(e.getFormat()),
                DataJobStatus.valueOf(e.getStatus()),
                e.getRowCount(),
                e.getByteSize(),
                e.getContentSha256(),
                e.getArtifactPath(),
                e.getLogPath(),
                e.getErrorType(),
                e.getErrorMessage(),
                e.getSubmittedAt(),
                e.getStartedAt(),
                e.getCompletedAt(),
                e.getHeartbeatAt(),
                e.getExpiresAt(),
                e.getScheduleId()
        );
    }
}
