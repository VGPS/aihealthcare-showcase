package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Shared async runner for admin pipeline triggers. Wraps any pipeline
 * work in a background thread, records the result via PipelineHealthService,
 * and returns a 202 Accepted response immediately.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class PipelineAsyncRunner {

    private final Executor executor;
    private final PipelineHealthService healthService;

    public PipelineAsyncRunner(PipelineHealthService healthService) {
        log.debug("PipelineAsyncRunner() | healthService={}", healthService);
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(3);
        pool.setMaxPoolSize(6);
        pool.setQueueCapacity(25);
        pool.setThreadNamePrefix("pipeline-async-");
        pool.initialize();
        this.executor = pool;
        this.healthService = healthService;
    }

    /**
     * Runs pipeline work asynchronously and returns a 202 response immediately.
     *
     * @param pipelineId the pipeline identifier (must match buildPipelineList IDs)
     * @param work       the pipeline work to execute in background
     * @return 202 Accepted with started=true metadata
     */
    public ResponseEntity<Map<String, Object>> runAsync(String pipelineId, Runnable work) {
        log.debug("runAsync() | pipelineId={}", pipelineId);

        executor.execute(() -> {
            Instant start = Instant.now();
            try {
                work.run();
                PipelineHealthService.PipelineRunRecord record =
                        PipelineHealthService.PipelineRunRecord.success(pipelineId, 1, start, Instant.now());
                healthService.recordRun(pipelineId, record);
                log.info("runAsync() | pipeline={} completed in {}ms", pipelineId, record.durationMs());
            } catch (Exception ex) {
                PipelineHealthService.PipelineRunRecord record =
                        PipelineHealthService.PipelineRunRecord.failure(pipelineId, ex.getMessage(), start, Instant.now());
                healthService.recordRun(pipelineId, record);
                log.error("runAsync() | pipeline={} failed: {}", pipelineId, ex.getMessage(), ex);
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("started", true);
        result.put("pipelineId", pipelineId);
        result.put("message", "Pipeline started in background. Check run history for results.");

        log.debug("runAsync() | return=202 Accepted, pipelineId={}", pipelineId);
        return ResponseEntity.accepted().body(result);
    }
}
