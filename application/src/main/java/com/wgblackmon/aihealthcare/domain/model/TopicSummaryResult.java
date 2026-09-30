package com.wgblackmon.aihealthcare.domain.model;

/**
 * Result returned by the anti-slop pipeline when generating a topic summary.
 *
 * <p>Carries the generated text alongside quality metadata so the caller can
 * store both the prose and the pipeline provenance in a single database row.
 * The {@code pipelineVersion} field identifies which generation pipeline was
 * used (e.g. {@code "anti-slop-v1"}), enabling the UI to distinguish freshly
 * linted summaries from legacy single-call summaries.
 *
 * @param text            The generated summary prose; must not be blank.
 * @param pipelineVersion Identifier for the pipeline that produced this text
 *                        (e.g. {@code "anti-slop-v1"}); never null.
 * @param lintScore       SlopLinter score 0–100 for the generated text.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-29
 * @updated 2026-09-29
 */
public record TopicSummaryResult(String text, String pipelineVersion, int lintScore) {

    public TopicSummaryResult {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (pipelineVersion == null || pipelineVersion.isBlank()) {
            throw new IllegalArgumentException("pipelineVersion must not be blank");
        }
    }
}
