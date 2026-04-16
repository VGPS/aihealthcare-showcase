package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record representing a newsletter topic.
 *
 * <p>A {@code Topic} is the top-level organizational unit for the harvesting,
 * summarization, and delivery pipeline.  Every feed source, harvested article,
 * AI-generated summary, and newsletter delivery is scoped to a {@code Topic}.
 * This enables the engine to serve multiple independent newsletters (e.g.
 * "AI Healthcare", "AI Legal", "AI Finance") from a single shared infrastructure
 * without code changes.
 *
 * <p>The {@code promptContext} field is injected into the Spring AI
 * {@code PromptTemplate} at summarization time, allowing each topic to
 * receive domain-appropriate AI instructions without hardcoding.
 *
 * <p>In Slice 1, {@code Topic} instances are constructed directly in tests and
 * configuration.  Slice 2 will introduce persistence and load them from the
 * database at application startup.
 *
 * @param id             Surrogate primary key; used as a FK in feed sources and articles.
 * @param name           Human-readable topic label (e.g., "AI Healthcare").
 * @param slug           URL-safe identifier used in routing and namespacing
 *                       (e.g., "ai-healthcare").
 * @param promptContext  Domain focus injected into AI summarization prompts
 *                       (e.g., "Focus on clinical AI, FDA approvals, and
 *                       healthcare interoperability.").
 * @param tone           Desired newsletter tone injected into prompts
 *                       (e.g., "Professional and concise").
 * @param active         Whether this topic participates in scheduled harvesting.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-10
 * @updated 2026-04-10
 */
public record Topic(
        Long id,
        String name,
        String slug,
        String promptContext,
        String tone,
        boolean active
) {}
