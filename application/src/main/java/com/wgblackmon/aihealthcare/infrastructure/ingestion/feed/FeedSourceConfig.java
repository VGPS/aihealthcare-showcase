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
 * <p>The optional {@code keywords} list applies only to {@code COMPETITOR}-tier sources
 * processed by {@link com.wgblackmon.aihealthcare.infrastructure.ingestion.web.WebPageHarvester}.
 * When non-empty, a page change is only emitted as an article if at least one keyword
 * appears (case-insensitive) in the extracted page content.  An empty list disables
 * filtering (all changes are accepted).
 *
 * @param topicId     FK to {@code Topic.id} — scopes this feed to a newsletter topic.
 * @param name        Human-readable label used in logs, metrics, and {@code sourceName} on articles.
 * @param topic       Grouping label stamped on every harvested article's {@code topic} field.
 *                    Multiple feeds can share the same topic so articles appear under one
 *                    section header on the news listing page.  Falls back to {@code name}
 *                    when {@code null} or blank.
 * @param url         Fully-qualified RSS, Atom, or web page URL.
 * @param tier        Harvest tier controlling scheduling and scoring weight.
 * @param baseWeight  Baseline relevance multiplier applied before AI scoring
 *                    (0.0 – 1.0; higher = more trusted source).
 * @param maxItems    Maximum number of items to ingest per harvest cycle (RSS/Atom only).
 * @param keywords    Optional keyword filter for COMPETITOR-tier web pages.  Empty = no filter.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-04-10
 * @updated 2026-05-20
 */
public record FeedSourceConfig(
        Long topicId,
        String name,
        String topic,
        String url,
        FeedTier tier,
        double baseWeight,
        int maxItems,
        java.util.List<String> keywords
) {

    /**
     * Returns the effective topic — the explicit {@code topic} if set, otherwise {@code name}.
     *
     * @return non-null topic label for article grouping
     */
    public String effectiveTopic() {
        return (topic != null && !topic.isBlank()) ? topic : name;
    }

    /**
     * Returns {@code true} if the source has no keyword filter (empty list) or if at least
     * one configured keyword appears (case-insensitive) in the given text.
     *
     * @param text the page content to test against the keyword list
     * @return {@code true} if the content should produce an article
     */
    public boolean matchesKeywords(String text) {
        if (keywords.isEmpty()) return true;
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    /**
     * Harvest tier for a feed source.
     */
    public enum FeedTier {
        /** Peer-reviewed journals and pre-print servers. Harvested daily. */
        ACADEMIC,
        /** Government and regulatory bodies. Harvested daily. */
        REGULATORY,
        /** Trade news and industry publications. Harvested every few hours. */
        INDUSTRY,
        /** Competitor web pages monitored for content changes. Harvested daily. */
        COMPETITOR,
        /** HuggingFace model registry. Harvested daily. */
        HUGGINGFACE,
        /** Perplexity Sonar API — deep-research harvest. Harvested daily when API key present. */
        PERPLEXITY
    }
}
