package com.wgblackmon.aihealthcare.domain.model;

/**
 * Discriminator that selects the active research pipeline for a {@link ResearchRequest}.
 *
 * <p>{@code LEGACY_GOOGLE} routes the request through the existing article-ingestion
 * store ({@code ArticleIngestionPort}) and skips AI planning and synthesis — behaviour
 * is identical to the pre-Slice-12 market-intelligence flow and is always safe to use
 * even without external API keys.
 *
 * <p>{@code STAGED_RESEARCH} activates the full planning → retrieval → synthesis →
 * citation-assembly pipeline introduced in Slice 12.  The Perplexity Sonar adapter is
 * attempted first; it falls back gracefully to the legacy adapter when no
 * {@code PERPLEXITY_API_KEY} is configured.
 *
 * <p>The default configured mode is controlled by the
 * {@code aihealthcare.research.mode} property and can be overridden per-request.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public enum ResearchMode {

    /**
     * Use stored articles from the existing ingestion pipeline.
     * No AI planning or synthesis is performed; safe with no API keys.
     */
    LEGACY_GOOGLE,

    /**
     * Use the full staged pipeline: AI planning, Perplexity retrieval (with
     * legacy fallback), AI synthesis, and citation assembly.
     */
    STAGED_RESEARCH,

    /**
     * Use both the ingestion DB (historical depth) and Perplexity Sonar (current
     * AI-curated sources). Sources from both are merged, deduplicated by URL, then
     * AI-synthesized into thematic sections with inline citations.
     *
     * <p>Requires the same API keys as {@code STAGED_RESEARCH}. Provides the broadest
     * possible source coverage: real-time web content plus the full historical corpus.
     */
    COMBINED
}
