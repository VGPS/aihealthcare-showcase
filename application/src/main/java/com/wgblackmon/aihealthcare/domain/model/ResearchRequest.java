package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record representing a single research request submitted to the
 * {@link com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase}.
 *
 * <p>{@code mode} is nullable — when {@code null} the orchestrator substitutes the
 * configured default ({@code aihealthcare.research.mode}).  This allows callers to
 * omit the mode and rely on environment-level defaults.
 *
 * <p>{@code topicHint} is an optional, narrower context string that the planning
 * service can use to focus sub-query decomposition (e.g., "AI diagnostics" within a
 * broader "healthcare AI" query).
 *
 * @param query       The research question; must not be blank.
 * @param mode        Desired pipeline mode; {@code null} means "use configured default".
 * @param topicHint   Optional domain hint to focus retrieval (e.g., "clinical AI").
 * @param maxSources  Maximum number of source documents to retrieve; must be ≥ 1.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public record ResearchRequest(
        String query,
        ResearchMode mode,
        String topicHint,
        int maxSources
) {
    /** Compact canonical constructor — validates required fields. */
    public ResearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (maxSources < 1) {
            throw new IllegalArgumentException("maxSources must be >= 1");
        }
    }
}
