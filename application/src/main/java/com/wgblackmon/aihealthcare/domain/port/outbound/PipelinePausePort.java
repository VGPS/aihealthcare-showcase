package com.wgblackmon.aihealthcare.domain.port.outbound;

/**
 * Outbound port for an operator-controlled kill switch on auto-sending
 * scheduled pipelines.
 *
 * <p>Unlike {@link NewsletterAutoSendPort} (a date-scoped override that
 * auto-resets after a manual send), this is a sticky on/off pause keyed
 * by pipeline identifier — intended for schedulers that build and deliver
 * content in a single step with no persisted draft to review beforehand
 * (e.g. the FREE-tier digest, the enterprise push sweeper). An admin sets
 * the pause when investigating a suspected bad output and clears it
 * manually once satisfied; it does not reset itself.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-16
 * @updated 2026-10-16
 */
public interface PipelinePausePort {

    /**
     * Returns true if the given pipeline is currently paused.
     *
     * @param pipelineKey stable identifier for the paused pipeline (e.g. {@code "digest-send"})
     * @return true if sends for this pipeline should be skipped
     */
    boolean isPaused(String pipelineKey);

    /**
     * Sets or clears the pause for the given pipeline.
     *
     * @param pipelineKey stable identifier for the paused pipeline
     * @param paused      true to pause (skip sends), false to resume
     */
    void setPaused(String pipelineKey, boolean paused);
}
