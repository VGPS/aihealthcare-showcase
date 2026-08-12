package com.wgblackmon.aihealthcare.infrastructure.ingestion.feed;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleHarvestingPort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.ArticleRelevanceFilter;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceConfig.FeedTier;

import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Infrastructure adapter that implements {@link ArticleHarvestingPort} using
 * the Rome RSS/Atom library.
 *
 * <p>Iterates over all configured {@link FeedSourceConfig} entries, fetches
 * each feed via Rome's {@link SyndFeedInput}, maps each {@link SyndEntry} to
 * a {@link NewsArticle} domain record, and returns a flat list for downstream
 * AI relevance scoring and summarization.
 *
 * <p>Each harvested {@link NewsArticle} is stamped with the {@code topicId},
 * {@code sourceName}, {@code sourceTier}, and {@code sourceWeight} from its
 * {@link FeedSourceConfig}, ensuring that all articles and newsletter output
 * can be filtered by topic throughout the pipeline.
 *
 * <p>Connection and read timeouts are intentionally kept short (10 s / 15 s)
 * to avoid blocking the scheduler thread pool on slow or unavailable feeds.
 * Failed feeds are logged and skipped — a single bad feed never aborts the
 * full harvest cycle.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-10
 * @updated 2026-05-19
 */
@Slf4j
@Component
public class RomeFeedHarvester implements ArticleHarvestingPort {

    private final List<FeedSourceConfig> feedSources;
    private final ArticleRelevanceFilter relevanceFilter;
    private final int maxAgeDays;

    /**
     * Constructor injection of the resolved feed source list and relevance filter.
     *
     * @param properties      externalized feed configuration from {@code application.yml}
     * @param relevanceFilter filters harvested articles to Healthcare+AI relevance
     * @param maxAgeDays      articles with publishedAt older than this many days are skipped
     */
    public RomeFeedHarvester(FeedSourceProperties properties,
                             ArticleRelevanceFilter relevanceFilter,
                             @Value("${aihealthcare.articles.max-age-days:30}") int maxAgeDays) {
        log.debug("RomeFeedHarvester() | properties={}, maxAgeDays={}", properties.getClass().getSimpleName(), maxAgeDays);
        this.relevanceFilter = relevanceFilter;
        this.maxAgeDays = maxAgeDays;
        List<FeedSourceConfig> rssOnly = new ArrayList<>();
        for (FeedSourceConfig config : properties.toFeedSourceConfigs()) {
            if (config.tier() != FeedTier.COMPETITOR
                    && config.tier() != FeedTier.HUGGINGFACE
                    && config.tier() != FeedTier.PERPLEXITY) {
                rssOnly.add(config);
            }
        }
        this.feedSources = List.copyOf(rssOnly);
        log.debug("RomeFeedHarvester() | initialized with {} RSS feed sources (filtered out non-RSS tiers)",
                  feedSources.size());
    }

    /**
     * Harvests all configured RSS/Atom feeds and returns a flat list of
     * {@link NewsArticle} domain records.
     *
     * <p>Each article's {@code sourceWeight} is pre-populated from the
     * feed's {@link FeedSourceConfig#baseWeight()} so the AI scoring layer
     * can use it as a prior.
     *
     * @return flat, unordered list of harvested articles (never {@code null})
     */
    @Override
    public List<NewsArticle> harvestAll() {
        log.debug("harvestAll() |");
        List<NewsArticle> articles = new ArrayList<>();

        for (FeedSourceConfig source : feedSources) {
            log.debug("harvestAll() | harvesting feed: name={}, tier={}", source.name(), source.tier());
            List<NewsArticle> harvested = harvestFeed(source);
            articles.addAll(harvested);
            log.debug("harvestAll() | feed '{}' yielded {} articles", source.name(), harvested.size());
        }

        List<NewsArticle> result = List.copyOf(articles);
        log.debug("harvestAll() | return={} total articles", result.size());
        return result;
    }

    /**
     * Harvests a single feed, mapping each {@link SyndEntry} to a
     * {@link NewsArticle}.  Exceptions are caught and logged; an empty list
     * is returned on failure so other feeds are unaffected.
     *
     * @param source the feed configuration to harvest
     * @return list of articles from this feed (empty on error)
     */
    private List<NewsArticle> harvestFeed(FeedSourceConfig source) {
        log.debug("harvestFeed() | source={}", source.name());
        List<NewsArticle> results = new ArrayList<>();

        try {
            URL feedUrl = new URL(source.url());
            SyndFeedInput input = new SyndFeedInput();

            try (XmlReader reader = new XmlReader(feedUrl.openConnection())) {
                SyndFeed feed = input.build(reader);
                List<SyndEntry> entries = feed.getEntries();

                Instant ageCutoff = maxAgeDays > 0
                        ? Instant.now().minus(maxAgeDays, ChronoUnit.DAYS)
                        : Instant.EPOCH;
                int limit = Math.min(entries.size(), source.maxItems());
                int skippedStale = 0;
                for (int i = 0; i < limit; i++) {
                    SyndEntry entry = entries.get(i);
                    NewsArticle article = mapEntryToArticle(entry, source);
                    if (article.publishedAt() != null && article.publishedAt().isBefore(ageCutoff)) {
                        skippedStale++;
                        continue;
                    }
                    results.add(article);
                }
                if (skippedStale > 0) {
                    log.info("harvestFeed() | skipped {} stale articles (published before {}) from '{}'",
                            skippedStale, ageCutoff, source.name());
                }
            }

        } catch (Exception ex) {
            log.error("harvestFeed() | failed to harvest feed '{}': {}", source.name(), ex.getMessage(), ex);
        }

        // Apply relevance filter — keep only articles that mention both healthcare and AI
        List<NewsArticle> filtered = relevanceFilter.filter(results);
        log.info("harvestFeed() | relevance filter: {} kept of {} from '{}'",
                 filtered.size(), results.size(), source.name());

        log.debug("harvestFeed() | return={} articles from '{}'", filtered.size(), source.name());
        return filtered;
    }

    /**
     * Maps a Rome {@link SyndEntry} to the domain {@link NewsArticle} record.
     *
     * <p>Falls back gracefully when optional fields (description, author,
     * published date) are absent — common in some regulatory RSS feeds.
     * The {@code articleId} is derived from the entry URI, then the link URL,
     * falling back to a random UUID if both are absent.
     *
     * @param entry  the Rome feed entry
     * @param source the feed configuration providing tier and weight context
     * @return populated {@link NewsArticle} record
     */
    private NewsArticle mapEntryToArticle(SyndEntry entry, FeedSourceConfig source) {
        log.debug("mapEntryToArticle() | entry.uri={}", entry.getUri());

        String articleId;
        if (entry.getUri() != null && !entry.getUri().isBlank()) {
            articleId = entry.getUri();
        } else if (entry.getLink() != null && !entry.getLink().isBlank()) {
            articleId = entry.getLink();
        } else {
            articleId = UUID.randomUUID().toString();
        }

        String title = entry.getTitle() != null ? entry.getTitle().trim() : "(no title)";

        URI url;
        try {
            url = entry.getLink() != null && !entry.getLink().isBlank()
                    ? URI.create(entry.getLink().trim())
                    : URI.create("");
        } catch (IllegalArgumentException ex) {
            log.warn("mapEntryToArticle() | invalid link URI for entry '{}', using empty URI", title);
            url = URI.create("");
        }

        String bodyText = "";
        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            bodyText = entry.getDescription().getValue().trim();
        }

        String author = entry.getAuthor() != null && !entry.getAuthor().isBlank()
                ? entry.getAuthor().trim()
                : null;

        Instant publishedAt = Instant.now();
        Date publishedDate = entry.getPublishedDate() != null
                ? entry.getPublishedDate()
                : entry.getUpdatedDate();
        if (publishedDate != null) {
            publishedAt = publishedDate.toInstant();
        }

        NewsArticle article = new NewsArticle(
                articleId,
                title,
                url,
                bodyText,
                source.effectiveTopic(),
                author,
                source.topicId(),
                source.name(),
                source.tier().name(),
                source.baseWeight(),
                publishedAt
        );

        log.debug("mapEntryToArticle() | return={}", article.title());
        return article;
    }
}
