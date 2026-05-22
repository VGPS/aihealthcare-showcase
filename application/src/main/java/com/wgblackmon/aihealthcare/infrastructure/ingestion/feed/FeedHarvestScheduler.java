package com.wgblackmon.aihealthcare.infrastructure.ingestion.feed;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.service.TopicSummaryGenerationService;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled driver that triggers {@link RomeFeedHarvester} at tier-appropriate
 * cadences, filters harvested articles by tier, and persists them via
 * {@link ArticleStoragePort}.
 *
 * <p>Tier cadences:
 * <ul>
 *   <li><b>ACADEMIC + REGULATORY</b> – daily at 06:00 UTC via {@link #harvestDailyFeeds()}</li>
 *   <li><b>INDUSTRY</b>              – every 4 hours via {@link #harvestIndustryFeeds()}</li>
 * </ul>
 *
 * <p>Duplicate articles are silently skipped by {@link ArticleStoragePort#save}
 * (dedup by URL), so repeated scheduler invocations are safe.
 *
 * <p>Every harvested {@link NewsArticle} carries a {@code topicId} propagated
 * from its {@link FeedSourceConfig}.  Downstream processing in Slice 2b will
 * use this to scope AI summarization and vector embeddings to the correct
 * {@link com.wgblackmon.aihealthcare.domain.model.Topic}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-10
 * @updated 2026-05-22
 */
@Slf4j
@Component
public class FeedHarvestScheduler {

    private final ArticleHarvestingPort harvestingPort;
    private final ArticleStoragePort    articleStoragePort;
    private final TopicSummaryGenerationService topicSummaryService;
    private final NewsTopicProperties newsTopicProperties;

    public FeedHarvestScheduler(ArticleHarvestingPort harvestingPort,
                                ArticleStoragePort articleStoragePort,
                                TopicSummaryGenerationService topicSummaryService,
                                NewsTopicProperties newsTopicProperties) {
        log.debug("FeedHarvestScheduler() | harvestingPort={}, articleStoragePort={}, topicSummaryService={}, newsTopicProperties={}",
                  harvestingPort.getClass().getSimpleName(),
                  articleStoragePort.getClass().getSimpleName(),
                  topicSummaryService.getClass().getSimpleName(),
                  newsTopicProperties.getClass().getSimpleName());
        this.harvestingPort     = harvestingPort;
        this.articleStoragePort = articleStoragePort;
        this.topicSummaryService = topicSummaryService;
        this.newsTopicProperties = newsTopicProperties;
    }

    /**
     * Runs a full harvest of all tiers on application startup so the news
     * listing page is pre-populated with articles and AI summaries.
     */
    @PostConstruct
    public void harvestOnStartup() {
        log.info("harvestOnStartup() | running full harvest on application startup");
        try {
            List<NewsArticle> all = harvestingPort.harvestAll();
            if (!all.isEmpty()) {
                routeForProcessing(all);
            }
            log.info("harvestOnStartup() | {} articles harvested and saved", all.size());
            generateTopicSummaries();
        } catch (Exception e) {
            log.warn("harvestOnStartup() | startup harvest failed — app continues normally", e);
        }
        log.debug("harvestOnStartup() | return=void");
    }

    /**
     * Daily harvest for ACADEMIC and REGULATORY tier feeds.
     * Runs at 06:00 UTC every day. Cron: {@code 0 0 6 * * *}.
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "UTC")
    public void harvestDailyFeeds() {
        log.debug("harvestDailyFeeds() | starting daily ACADEMIC + REGULATORY harvest");
        List<NewsArticle> all = harvestingPort.harvestAll();

        List<NewsArticle> dailyArticles = new ArrayList<>();
        for (NewsArticle article : all) {
            if ("ACADEMIC".equals(article.sourceTier()) ||
                "REGULATORY".equals(article.sourceTier())) {
                dailyArticles.add(article);
            }
        }

        log.info("harvestDailyFeeds() | {} ACADEMIC/REGULATORY articles harvested", dailyArticles.size());
        routeForProcessing(dailyArticles);
        generateTopicSummaries();
        log.debug("harvestDailyFeeds() | return=void");
    }

    /**
     * High-frequency harvest for INDUSTRY tier feeds.
     * Runs every 4 hours. Fixed-rate: 14_400_000 ms.
     */
    @Scheduled(fixedRateString = "#{14400 * 1000}")
    public void harvestIndustryFeeds() {
        log.debug("harvestIndustryFeeds() | starting 4-hour INDUSTRY harvest");
        List<NewsArticle> all = harvestingPort.harvestAll();

        List<NewsArticle> industryArticles = new ArrayList<>();
        for (NewsArticle article : all) {
            if ("INDUSTRY".equals(article.sourceTier())) {
                industryArticles.add(article);
            }
        }

        log.info("harvestIndustryFeeds() | {} INDUSTRY articles harvested", industryArticles.size());
        routeForProcessing(industryArticles);
        generateTopicSummaries();
        log.debug("harvestIndustryFeeds() | return=void");
    }

    /**
     * Triggers AI topic summary generation for all configured topics.
     * Failures are caught so the harvest pipeline is never interrupted.
     */
    private void generateTopicSummaries() {
        log.debug("generateTopicSummaries() | starting topic summary generation");
        try {
            List<String> topics = newsTopicProperties.getTopics();
            topicSummaryService.generateSummaries(topics);
            log.info("generateTopicSummaries() | topic summary generation complete");
        } catch (Exception e) {
            log.warn("generateTopicSummaries() | topic summary generation failed — harvest continues", e);
        }
        log.debug("generateTopicSummaries() | return=void");
    }

    /**
     * Persists harvested articles to the DB via {@link ArticleStoragePort}.
     * Duplicate URLs are silently skipped by the adapter.
     *
     * @param articles filtered articles ready for persistence
     */
    private void routeForProcessing(List<NewsArticle> articles) {
        log.debug("routeForProcessing() | articles={}", articles.size());
        articleStoragePort.save(articles);
        log.info("routeForProcessing() | Saved {} articles to DB (duplicates silently skipped)",
                 articles.size());
        log.debug("routeForProcessing() | return=void");
    }
}
