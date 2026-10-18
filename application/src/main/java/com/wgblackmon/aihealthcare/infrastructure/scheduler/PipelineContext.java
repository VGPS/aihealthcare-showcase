package com.wgblackmon.aihealthcare.infrastructure.scheduler;

/**
 * Thread-local holder for the pipeline identifier of the currently executing
 * scheduled step.
 *
 * <p>Set at the start of each pipeline step (in {@link StartupPipelineOrchestrator}
 * and in the standalone schedulers whose own cron bypasses the orchestrator) and
 * cleared when the step finishes. {@link com.wgblackmon.aihealthcare.infrastructure.ai.TokenUsageLoggingAdvisor}
 * reads this to attribute each LLM call's token usage to the pipeline that made it.
 *
 * <p>Calls made with no context set (e.g. interactive web requests, not a
 * scheduled pipeline) are attributed to {@link #UNTAGGED}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public final class PipelineContext {

    /** Pipeline id used when no context has been set on the current thread. */
    public static final String UNTAGGED = "interactive";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private PipelineContext() {
    }

    /**
     * Sets the pipeline id for the current thread.
     *
     * @param pipelineId the canonical pipeline identifier
     */
    public static void set(String pipelineId) {
        CURRENT.set(pipelineId);
    }

    /**
     * Returns the pipeline id for the current thread, or {@link #UNTAGGED}
     * if none has been set.
     *
     * @return the current pipeline id, never null
     */
    public static String get() {
        String value = CURRENT.get();
        return value != null ? value : UNTAGGED;
    }

    /**
     * Clears the pipeline id for the current thread. Must be called in a
     * {@code finally} block after {@link #set(String)} to avoid leaking
     * context onto whatever this pooled thread runs next.
     */
    public static void clear() {
        CURRENT.remove();
    }
}
