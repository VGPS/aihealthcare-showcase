package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.exception.StepExecutionFailedException;
import com.wgblackmon.aihealthcare.domain.model.PipelineErrorType;
import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;
import com.wgblackmon.aihealthcare.domain.model.PipelineStepStatus;
import com.wgblackmon.aihealthcare.domain.model.RetryPolicy;
import com.wgblackmon.aihealthcare.domain.model.StepGateResult;
import com.wgblackmon.aihealthcare.domain.port.outbound.PipelineRunEventPort;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Executes a pipeline step with bounded retry, optional quality-gate evaluation,
 * and durable {@link PipelineRunEvent} audit logging.
 *
 * <p>Ported from the {@code PhaseExecutor} pattern used by the agentic SDLC
 * orchestration layer in the url-shortener prototype: each attempt is timed,
 * failures are classified via {@link PipelineErrorClassifier}, and only
 * retried when {@link PipelineErrorType#isRetryable()} allows it. A
 * {@link StepGateResult} lets a caller reject an attempt that completed
 * without throwing but whose output does not meet a minimum quality bar
 * (e.g. zero sources retrieved) — such rejections are retried exactly like
 * exceptions, with the gate's reason captured in the audit trail.
 *
 * <p>{@code eventPort} may be {@code null} — if so, events are skipped rather
 * than failing the step, mirroring the {@code @Autowired(required = false)}
 * pattern used throughout the pipeline infrastructure.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-16
 * @updated 2026-10-16
 */
public class ResilientStepExecutor {

    private final DomainLogger log = new DomainLogger(ResilientStepExecutor.class);
    private final PipelineRunEventPort eventPort;

    public ResilientStepExecutor(PipelineRunEventPort eventPort) {
        this.eventPort = eventPort;
    }

    /** Executes {@code step} with bounded retry on classified-retryable exceptions; no gate check. */
    public <T> T execute(String pipelineId, String stepName, RetryPolicy retryPolicy, Supplier<T> step) {
        return execute(pipelineId, stepName, retryPolicy, step, result -> StepGateResult.pass("no gate configured"));
    }

    /**
     * Executes {@code step} with bounded retry, retrying both thrown exceptions
     * (when classified as retryable) and results that fail {@code gate}.
     *
     * @throws StepExecutionFailedException when the first failure is classified as
     *         non-retryable, or all attempts are exhausted without a passing result
     */
    public <T> T execute(String pipelineId, String stepName, RetryPolicy retryPolicy,
                          Supplier<T> step, Function<T, StepGateResult> gate) {
        log.debug("execute() | pipelineId={}, stepName={}, maxAttempts={}",
                pipelineId, stepName, retryPolicy.maxAttempts());

        String lastFailureReason = null;

        for (int attempt = 0; attempt <= retryPolicy.maxAttempts(); attempt++) {
            if (attempt > 0) {
                long backoffMs = retryPolicy.backoffForAttempt(attempt - 1);
                log.debug("execute() | retrying pipelineId={}, stepName={}, attempt={}, backoffMs={}",
                        pipelineId, stepName, attempt, backoffMs);
                sleep(backoffMs);
            }

            Instant startedAt = Instant.now();
            try {
                T result = step.get();
                StepGateResult gateResult = gate.apply(result);
                Instant completedAt = Instant.now();

                if (gateResult.passed()) {
                    persistEvent(pipelineId, stepName, PipelineStepStatus.SUCCESS,
                            startedAt, completedAt, null, null, null);
                    return result;
                }

                lastFailureReason = gateResult.reason();
                persistEvent(pipelineId, stepName, PipelineStepStatus.FAILED,
                        startedAt, completedAt, "Gate failed: " + gateResult.reason(), null, null);
                log.warn("execute() | gate failed pipelineId={}, stepName={}, reason={}, remediation={}",
                        pipelineId, stepName, gateResult.reason(), gateResult.remediation());

            } catch (RuntimeException e) {
                Instant completedAt = Instant.now();
                PipelineErrorType errorType = PipelineErrorClassifier.classify(e);
                String provider = PipelineErrorClassifier.detectProvider(e);
                lastFailureReason = e.getMessage();

                persistEvent(pipelineId, stepName, PipelineStepStatus.FAILED,
                        startedAt, completedAt, e.getMessage(), errorType, provider);

                if (!errorType.isRetryable()) {
                    log.error("execute() | non-retryable failure pipelineId={}, stepName={}, errorType={}",
                            pipelineId, stepName, errorType);
                    throw new StepExecutionFailedException(stepName, errorType, e);
                }
                log.warn("execute() | retryable failure pipelineId={}, stepName={}, errorType={}, attempt={}, {}",
                        pipelineId, stepName, errorType, attempt, e);
            }
        }

        throw new StepExecutionFailedException(stepName, retryPolicy.maxAttempts(), lastFailureReason);
    }

    private void persistEvent(String pipelineId, String stepName, PipelineStepStatus status,
                               Instant startedAt, Instant completedAt, String errorMessage,
                               PipelineErrorType errorType, String errorProvider) {
        if (eventPort == null) {
            return;
        }
        try {
            long durationMs = Duration.between(startedAt, completedAt).toMillis();
            eventPort.save(new PipelineRunEvent(null, pipelineId, stepName, status,
                    startedAt, completedAt, durationMs, errorMessage, 0, "RESEARCH_ORCHESTRATOR",
                    errorType, errorProvider, null));
        } catch (Exception e) {
            log.warn("persistEvent() | failed to persist pipeline event for step={}: {}", stepName, e.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during retry backoff", ie);
        }
    }
}
