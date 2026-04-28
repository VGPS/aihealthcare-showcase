package com.wgblackmon.aihealthcare.web.dto;

/**
 * Response DTO for a single search engine prompt configuration.
 *
 * <p>Returned by {@code GET /api/v1/search-prompts} and
 * {@code GET /api/v1/search-prompts/{engine}}.
 *
 * @param engine       Search engine identifier (e.g. {@code "GOOGLE"}, {@code "PERPLEXITY"}).
 * @param name         Human-readable label.
 * @param templateText Full query template text with optional {@code {topic}} placeholder.
 * @param description  Description of the prompt's strategy and intent.
 * @param active       Whether this config is used during harvest runs.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
public record SearchPromptResponse(
        String engine,
        String name,
        String templateText,
        String description,
        boolean active
) {}
