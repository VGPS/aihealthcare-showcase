package com.wgblackmon.aihealthcare.infrastructure.enterprise;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.*;
import com.wgblackmon.aihealthcare.domain.service.DataSetRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;

/**
 * Async executor for enterprise data jobs.
 *
 * <p>Runs on the {@code enterpriseDataExecutor} thread pool. Lifecycle:
 * {@code RUNNING → fetch → render → write artifact → SUCCEEDED}, with a
 * heartbeat every 10 seconds and configurable timeout. Any exception maps
 * to {@code FAILED} plus an {@code errorType}.
 *
 * <p>Kept out of the domain service so the domain stays framework-free and
 * so ED-2 push schedules can submit to it directly.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class EnterpriseDataJobRunner {

    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;

    private final Map<String, EnterpriseDataSourcePort> feedRegistry;
    private final DataJobPort dataJobPort;
    private final DataJobLogPort dataJobLogPort;
    private final DataArtifactPort dataArtifactPort;
    private final DataAccessAuditPort auditPort;
    private final long jobTimeoutMs;

    public EnterpriseDataJobRunner(List<EnterpriseDataSourcePort> sources,
                                   DataJobPort dataJobPort,
                                   DataJobLogPort dataJobLogPort,
                                   DataArtifactPort dataArtifactPort,
                                   DataAccessAuditPort auditPort,
                                   long jobTimeoutMs) {
        log.debug("EnterpriseDataJobRunner() | sources={}, timeoutMs={}", sources.size(), jobTimeoutMs);
        this.feedRegistry = sources.stream()
                .collect(Collectors.toMap(EnterpriseDataSourcePort::feedId, Function.identity()));
        this.dataJobPort = dataJobPort;
        this.dataJobLogPort = dataJobLogPort;
        this.dataArtifactPort = dataArtifactPort;
        this.auditPort = auditPort;
        this.jobTimeoutMs = jobTimeoutMs;
    }

    /**
     * Executes a submitted data job asynchronously.
     *
     * @param request the plan-resolved, row-limit-clamped request
     */
    @Async("enterpriseDataExecutor")
    public void execute(DataRequest request) {
        log.debug("execute() | jobId={}, feedId={}, format={}", request.jobId(), request.feedId(), request.format());

        DataJobLog jobLog = dataJobLogPort.open(request.jobId());
        ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            // Mark RUNNING
            Instant startedAt = Instant.now();
            dataJobPort.updateStatus(request.jobId(), DataJobStatus.RUNNING,
                    null, null, null, null, null, null, null);
            dataJobPort.touchHeartbeat(request.jobId(), startedAt);
            jobLog.phase("JOB_START", "feedId=" + request.feedId() + " format=" + request.format());

            // Start heartbeat
            heartbeatScheduler.scheduleAtFixedRate(
                    () -> {
                        try {
                            dataJobPort.touchHeartbeat(request.jobId(), Instant.now());
                        } catch (Exception e) {
                            log.warn("execute() | Heartbeat failed for job {}: {}", request.jobId(), e.getMessage());
                        }
                    },
                    HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

            // Fetch
            jobLog.phase("FETCH_START", "feed=" + request.feedId());
            EnterpriseDataSourcePort source = feedRegistry.get(request.feedId());
            if (source == null) {
                throw new IllegalStateException("Feed not found in registry: " + request.feedId());
            }

            DataSet dataSet = executeWithTimeout(() -> source.fetch(request, jobLog));
            jobLog.phase("FETCH_END", "rows=" + dataSet.rows().size()
                    + " columns=" + dataSet.columns().size());

            // Render
            jobLog.phase("RENDER_START", "format=" + request.format());
            byte[] content = DataSetRenderer.render(dataSet, request.format());
            jobLog.phase("RENDER_END", "bytes=" + content.length);

            // Write artifact
            jobLog.phase("ARTIFACT_WRITE", "bytes=" + content.length);
            DataArtifact artifact = dataArtifactPort.write(
                    request.jobId(), request.format(), content);

            // Compute SHA-256
            String sha256 = sha256Hex(content);

            // Mark SUCCEEDED
            Instant completedAt = Instant.now();
            dataJobPort.updateStatus(request.jobId(), DataJobStatus.SUCCEEDED,
                    null, null,
                    dataSet.rows().size(), (long) content.length, sha256,
                    artifact.fileName(), completedAt);

            auditPort.append(new DataAccessAuditEntry(
                    completedAt, request.ownerEmail(), request.jobId(), null,
                    DataAccessAction.COMPLETE, "SUCCESS",
                    "rows=" + dataSet.rows().size() + " bytes=" + content.length,
                    dataSet.rows().size(), (long) content.length));

            jobLog.phase("JOB_END", "status=SUCCEEDED rows=" + dataSet.rows().size());

        } catch (TimeoutException e) {
            handleFailure(request, jobLog, "TIMEOUT", "Job exceeded timeout of " + jobTimeoutMs + "ms", e);
        } catch (IllegalStateException | IllegalArgumentException e) {
            handleFailure(request, jobLog, "VALIDATION", e.getMessage(), e);
        } catch (Exception e) {
            handleFailure(request, jobLog, "EXECUTION", e.getMessage(), e);
        } finally {
            heartbeatScheduler.shutdownNow();
        }

        log.debug("execute() | return=void jobId={}", request.jobId());
    }

    private <T> T executeWithTimeout(Callable<T> task) throws Exception {
        ExecutorService singleThread = Executors.newSingleThreadExecutor();
        try {
            Future<T> future = singleThread.submit(task);
            return future.get(jobTimeoutMs, TimeUnit.MILLISECONDS);
        } finally {
            singleThread.shutdownNow();
        }
    }

    private void handleFailure(DataRequest request, DataJobLog jobLog,
                                String errorType, String errorMessage, Exception cause) {
        log.warn("execute() | Job {} failed: type={}, message={}", request.jobId(), errorType, errorMessage);
        try {
            Instant completedAt = Instant.now();
            dataJobPort.updateStatus(request.jobId(), DataJobStatus.FAILED,
                    errorType, errorMessage, null, null, null, null, completedAt);
            auditPort.append(new DataAccessAuditEntry(
                    completedAt, request.ownerEmail(), request.jobId(), null,
                    DataAccessAction.FAIL, "FAILURE",
                    "errorType=" + errorType, null, null));
            jobLog.error("JOB_FAIL", errorType + ": " + errorMessage, cause);
        } catch (Exception e) {
            log.error("execute() | Failed to record failure for job {}: {}",
                    request.jobId(), e.getMessage());
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
