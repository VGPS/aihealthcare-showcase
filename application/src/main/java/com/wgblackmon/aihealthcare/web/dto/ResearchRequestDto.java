package com.wgblackmon.aihealthcare.web.dto;

/**
 * Request body DTO for {@code POST /api/v1/research}.
 *
 * <p>All fields except {@code query} are optional.  The controller applies defaults:
 * {@code mode} defaults to {@code "LEGACY_GOOGLE"} and {@code maxSources} defaults to
 * {@code 20} when not provided.
 *
 * @param query       The research question; required, must not be blank.
 * @param mode        Pipeline mode: {@code "LEGACY_GOOGLE"} or {@code "STAGED_RESEARCH"}.
 *                    Defaults to {@code "LEGACY_GOOGLE"} when omitted.
 * @param topicHint   Optional narrowing hint (e.g., {@code "clinical AI diagnostics"}).
 * @param maxSources  Maximum number of sources to retrieve; defaults to {@code 20}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public record ResearchRequestDto(
        String query,
        String mode,
        String topicHint,
        Integer maxSources
) {}
