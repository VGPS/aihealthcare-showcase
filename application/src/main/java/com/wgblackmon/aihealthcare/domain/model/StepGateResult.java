package com.wgblackmon.aihealthcare.domain.model;

/**
 * Pass/fail verdict for a completed pipeline step's output, used by
 * {@link com.wgblackmon.aihealthcare.domain.service.ResilientStepExecutor} and
 * callers such as {@link com.wgblackmon.aihealthcare.domain.service.ResearchOrchestratorService}
 * to decide whether a non-throwing result still warrants a fallback (e.g. zero
 * sources retrieved from the primary adapter).
 *
 * <p>Ported from the {@code GateResult} record used by the url-shortener prototype's
 * agentic SDLC orchestration layer ({@code PhaseGatePort}).
 *
 * @param passed      Whether the output meets the minimum quality bar.
 * @param reason      Human-readable explanation of the verdict.
 * @param remediation What the caller should try instead; null when passed.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-16
 * @updated 2026-10-16
 */
public record StepGateResult(boolean passed, String reason, String remediation) {

    public static StepGateResult pass(String reason) {
        return new StepGateResult(true, reason, null);
    }

    public static StepGateResult fail(String reason, String remediation) {
        return new StepGateResult(false, reason, remediation);
    }
}
