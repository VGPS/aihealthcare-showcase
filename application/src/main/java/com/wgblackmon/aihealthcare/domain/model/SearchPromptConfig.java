package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable configuration record for a search engine harvest prompt.
 *
 * <p>A {@code SearchPromptConfig} defines the query template sent to a specific
 * search engine (e.g. {@code GOOGLE} or {@code PERPLEXITY}) during the
 * article-harvest phase of the pipeline. It is distinct from
 * {@link PromptVariant}, which processes already-harvested content through an LLM.
 *
 * <p>The {@code templateText} field may contain a {@code {topic}} placeholder that
 * is substituted at harvest time with the newsletter topic being researched.
 *
 * <p>Only configs where {@code active} is {@code true} are used during scheduled
 * harvest runs. Inactive configs are retained for historical reference.
 *
 * @param engine       Search engine identifier — e.g. {@code "GOOGLE"} or
 *                     {@code "PERPLEXITY"}. Non-blank.
 * @param name         Human-readable label for the prompt configuration. Non-blank.
 * @param templateText Query template with optional {@code {topic}} placeholder.
 *                     Non-blank.
 * @param description  Free-text description of the prompt's strategy and intent.
 *                     May be blank or null.
 * @param active       When {@code true} this config is used during harvest runs.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
public record SearchPromptConfig(
        String engine,
        String name,
        String templateText,
        String description,
        boolean active
) {
    /**
     * Compact constructor — validates required fields.
     */
    public SearchPromptConfig {
        if (engine == null || engine.isBlank()) {
            throw new IllegalArgumentException("engine must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (templateText == null || templateText.isBlank()) {
            throw new IllegalArgumentException("templateText must not be blank");
        }
    }
}
