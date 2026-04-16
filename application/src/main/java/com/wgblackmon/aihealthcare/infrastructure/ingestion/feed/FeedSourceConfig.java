package com.wgblackmon.aihealthcare.infrastructure.ingestion.feed;

/**
 * Immutable configuration descriptor for a single RSS/Atom feed source.
 *
 * <p>Encapsulates all metadata needed by {@link RomeFeedHarvester} to fetch,
 * identify, and tier-weight articles from a given feed URL.  Instances are
 * constructed from {@link FeedSourceProperties} at application startup and held
 * in a {@link java.util.List} injected into the harvester.
 *
 * <p>The {@code topicId} field scopes this feed to a specific newsletter
 * {@link com.wgblackmon.aihealthcare.domain.model.Topic}.  During harvest, it
 * is propagated to every
 * {@link com.wgblackmon.aihealthcare.domain.model.NewsArticle} produced from
 * this feed so that articles, vector embeddings, and newsletter output can all
 * be filtered by topic without infrastructure changes.
 *
 * <p>The {@code tier} field drives scheduling and relevance-score weighting:
 * <ul>
 *   <li>ACADEMIC   – daily harvest, higher base relevance weight</li>
 *   <li>REGULATORY – event-driven / daily, very high signal value</li>
 *   <li>INDUSTRY   – frequent harvest (every few hours), needs AI filtering</li>
 * </ul>
 *
 * @param topicId     FK to {@code Topic.id} — scopes this feed to a newsletter topic.
 * @param name        Human-readable label used in logs and metrics.
 * @param url         Fully-qualified RSS or Atom feed URL.
 * @param tier        Harvest tier controlling scheduling and scoring weight.
 * @param baseWeight  Baseline relevance multiplier applied before AI scoring
 *                    (0.0 – 1.0; higher = more trusted source).
 * @param maxItems    Maximum number of items to ingest per harvest cycle.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-10
 * @updated 2026-04-10
 */
public record FeedSourceConfig(
        Long topicId,
        String name,
        String url,
        FeedTier tier,
        double baseWeight,
        int maxItems
) {

    /**
     * Harvest tier for a feed source.
     */
    public enum FeedTier {
        /** Peer-reviewed journals and pre-print servers. Harvested daily. */
        ACADEMIC,
        /** Government and regulatory bodies. Harvested daily. */
        REGULATORY,
        /** Trade news and industry publications. Harvested every few hours. */
        INDUSTRY
    }
}
