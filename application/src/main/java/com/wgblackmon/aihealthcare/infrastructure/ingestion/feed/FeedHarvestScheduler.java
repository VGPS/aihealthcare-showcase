package com.wgblackmon.aihealthcare.infrastructure.ingestion.feed;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;
import com.wgblackmon.aihealthcare.domain.model.LintReport;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.KnowledgeCompilationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.LintReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.domain.service.TopicSummaryGenerationService;
import com.wgblackmon.aihealthcare.domain.service.WatchlistMatchingService;
import com.wgblackmon.aihealthcare.domain.service.WikiLintService;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import com.wgblackmon.aihealthcare.infrastructure.delivery.WebhookDispatcher;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.StartupPipelineOrchestrator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled driver that triggers {@link RomeFeedHarvester} at tier-appropriate
 * cadences, filters harvested articles by tier, and persists them via
 * {@link ArticleStoragePort}.
 *
 * <p>Tier cadences (configurable via {@code application.yml}):
 * <ul>
 *   <li><b>ACADEMIC + REGULATORY</b> – daily via {@link #harvestDailyFeeds()}</li>
 *   <li><b>INDUSTRY</b>              – every N ms via {@link #harvestIndustryFeeds()}</li>
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
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class FeedHarvestScheduler {

    private final ArticleHarvestingPort harvestingPort;
    private final ArticleStoragePort    articleStoragePort;
    private final TopicSummaryGenerationService topicSummaryService;
    private final NewsTopicProperties newsTopicProperties;
    private final KnowledgeCompilationPort knowledgeCompilationPort;
    private final WikiLintService wikiLintService;
    private final WikiQueryPort wikiQueryPort;
    private final LintReportPort lintReportPort;
    private final WatchlistMatchingService watchlistMatchingService;
    private final WatchlistPort watchlistPort;
    private final WatchlistMatchPort watchlistMatchPort;
    private final StartupPipelineOrchestrator pipelineOrchestrator;
    private final WebhookDispatcher webhookDispatcher;

    @Value("${aihealthcare.startup.harvest-enabled:false}")
    private boolean startupHarvestEnabled;

    public FeedHarvestScheduler(ArticleHarvestingPort harvestingPort,
                                ArticleStoragePort articleStoragePort,
                                TopicSummaryGenerationService topicSummaryService,
                                NewsTopicProperties newsTopicProperties,
                                @Autowired(required = false) KnowledgeCompilationPort knowledgeCompilationPort,
                                @Autowired(required = false) WikiLintService wikiLintService,
                                @Autowired(required = false) WikiQueryPort wikiQueryPort,
                                @Autowired(required = false) LintReportPort lintReportPort,
                                @Autowired(required = false) WatchlistMatchingService watchlistMatchingService,
                                @Autowired(required = false) WatchlistPort watchlistPort,
                                @Autowired(required = false) WatchlistMatchPort watchlistMatchPort,
                                @Autowired(required = false) StartupPipelineOrchestrator pipelineOrchestrator,
                                @Autowired(required = false) WebhookDispatcher webhookDispatcher) {
        log.debug("FeedHarvestScheduler() | harvestingPort={}, articleStoragePort={}, topicSummaryService={}, newsTopicProperties={}, knowledgeCompilationPort={}",
                  harvestingPort.getClass().getSimpleName(),
                  articleStoragePort.getClass().getSimpleName(),
                  topicSummaryService.getClass().getSimpleName(),
                  newsTopicProperties.getClass().getSimpleName(),
                  knowledgeCompilationPort != null ? knowledgeCompilationPort.getClass().getSimpleName() : "null");
        this.harvestingPort     = harvestingPort;
        this.articleStoragePort = articleStoragePort;
        this.topicSummaryService = topicSummaryService;
        this.newsTopicProperties = newsTopicProperties;
        this.knowledgeCompilationPort = knowledgeCompilationPort;
        this.wikiLintService = wikiLintService;
        this.wikiQueryPort = wikiQueryPort;
        this.lintReportPort = lintReportPort;
        this.watchlistMatchingService = watchlistMatchingService;
        this.watchlistPort = watchlistPort;
        this.watchlistMatchPort = watchlistMatchPort;
        this.pipelineOrchestrator = pipelineOrchestrator;
        this.webhookDispatcher = webhookDispatcher;
    }

    /**
     * Runs a full harvest of all tiers on application startup so the news
     * listing page is pre-populated with articles and AI summaries.
     */
    @PostConstruct
    public void harvestOnStartup() {
        if (!startupHarvestEnabled) {
            log.info("harvestOnStartup() | startup harvest disabled (aihealthcare.startup.harvest-enabled=false) — relying on scheduled crons");
            return;
        }
        log.info("harvestOnStartup() | running full harvest on application startup");
        try {
            List<NewsArticle> all = harvestingPort.harvestAll();
            if (!all.isEmpty()) {
                routeForProcessing(all);
            }
            log.info("harvestOnStartup() | {} articles harvested and saved", all.size());
            generateTopicSummaries();
            if (pipelineOrchestrator != null) {
                pipelineOrchestrator.runAllPipelines();
            }
        } catch (Exception e) {
            log.warn("harvestOnStartup() | startup harvest failed — app continues normally", e);
        }
        log.debug("harvestOnStartup() | return=void");
    }

    /**
     * Daily harvest for ACADEMIC and REGULATORY tier feeds.
     * Cron configured via {@code aihealthcare.harvest.daily-cron} (default: 04:00 UTC).
     */
    @Scheduled(cron = "${aihealthcare.harvest.daily-cron}", zone = "UTC")
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
        compileWikiPages(dailyArticles);
        lintWikiPages();
        matchWatchlistItems(dailyArticles);
        if (pipelineOrchestrator != null) {
            pipelineOrchestrator.runAllPipelines();
        }
        log.debug("harvestDailyFeeds() | return=void");
    }

    /**
     * High-frequency harvest for INDUSTRY tier feeds.
     * Rate configured via {@code aihealthcare.harvest.industry-rate-ms} (default: 4h / 14400000 ms).
     */
    @Scheduled(fixedRateString = "${aihealthcare.harvest.industry-rate-ms}")
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
        compileWikiPages(industryArticles);
        lintWikiPages();
        matchWatchlistItems(industryArticles);
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
     * Triggers wiki compilation for the given articles.
     * Failures are caught so the harvest pipeline is never interrupted.
     * No-ops gracefully if the compilation port is not configured.
     *
     * @param articles articles to compile into the wiki knowledge base
     */
    private void compileWikiPages(List<NewsArticle> articles) {
        log.debug("compileWikiPages() | articles={}", articles.size());
        if (knowledgeCompilationPort == null) {
            log.debug("compileWikiPages() | knowledgeCompilationPort is null — skipping");
            log.debug("compileWikiPages() | return=void");
            return;
        }
        try {
            CompilationReport report = knowledgeCompilationPort.compileNewSources(articles);
            log.info("compileWikiPages() | created={}, updated={}, contradictions={}",
                    report.pagesCreated().size(), report.pagesUpdated().size(),
                    report.contradictionsFlagged().size());
        } catch (Exception e) {
            log.warn("compileWikiPages() | wiki compilation failed — harvest continues", e);
        }
        log.debug("compileWikiPages() | return=void");
    }

    /**
     * Runs the wiki linter after compilation to detect quality issues.
     * No-ops gracefully if the lint service or query port is not configured.
     * Failures are caught so the harvest pipeline is never interrupted.
     */
    private void lintWikiPages() {
        log.debug("lintWikiPages() | starting wiki lint");
        if (wikiLintService == null || wikiQueryPort == null || lintReportPort == null) {
            log.debug("lintWikiPages() | lint dependencies not configured — skipping");
            log.debug("lintWikiPages() | return=void");
            return;
        }
        try {
            List<WikiPage> allPages = wikiQueryPort.findRelevantPages("", 10000);
            LintReport report = wikiLintService.lint(allPages, 30);
            lintReportPort.save(report);

            int totalIssues = report.orphanedSlugs().size()
                    + report.brokenRefs().size()
                    + report.staleSlugs().size()
                    + report.missingProvenance().size();
            log.info("lintWikiPages() | {} pages checked, {} issues found", report.totalPagesChecked(), totalIssues);
        } catch (Exception e) {
            log.warn("lintWikiPages() | wiki lint failed — harvest continues", e);
        }
        log.debug("lintWikiPages() | return=void");
    }

    /**
     * Matches harvested articles against all subscriber watchlist items.
     * New matches are persisted; duplicates (same item+article) are skipped.
     * No-ops gracefully if watchlist dependencies are not configured.
     * Failures are caught so the harvest pipeline is never interrupted.
     *
     * @param articles articles to match against watchlists
     */
    private void matchWatchlistItems(List<NewsArticle> articles) {
        log.debug("matchWatchlistItems() | articles={}", articles.size());
        if (watchlistMatchingService == null || watchlistPort == null || watchlistMatchPort == null) {
            log.debug("matchWatchlistItems() | watchlist dependencies not configured — skipping");
            log.debug("matchWatchlistItems() | return=void");
            return;
        }
        try {
            List<WatchlistItem> allItems = watchlistPort.findAll();
            if (allItems.isEmpty()) {
                log.debug("matchWatchlistItems() | no watchlist items — skipping");
                log.debug("matchWatchlistItems() | return=void");
                return;
            }

            List<WatchlistMatch> matches = watchlistMatchingService.matchArticlesAgainstWatchlist(articles, allItems);

            // Filter out duplicates that already exist in DB
            List<WatchlistMatch> newMatches = new ArrayList<>();
            for (WatchlistMatch match : matches) {
                if (!watchlistMatchPort.existsByItemAndArticle(match.itemId(), match.articleId())) {
                    newMatches.add(match);
                }
            }

            if (!newMatches.isEmpty()) {
                watchlistMatchPort.saveAll(newMatches);
                notifyWatchlistMatches(newMatches.size());
            }
            log.info("matchWatchlistItems() | {} new matches from {} candidates", newMatches.size(), matches.size());
        } catch (Exception e) {
            log.warn("matchWatchlistItems() | watchlist matching failed — harvest continues", e);
        }
        log.debug("matchWatchlistItems() | return=void");
    }

    /**
     * Dispatches webhook notifications for new watchlist matches.
     * No-ops if the webhook dispatcher is not configured.
     */
    private void notifyWatchlistMatches(int matchCount) {
        log.debug("notifyWatchlistMatches() | matchCount={}", matchCount);
        if (webhookDispatcher == null) {
            log.debug("notifyWatchlistMatches() | webhookDispatcher not configured — skipping");
            log.debug("notifyWatchlistMatches() | return=void");
            return;
        }
        try {
            webhookDispatcher.dispatch(
                    WebhookEventType.WATCHLIST_MATCH,
                    matchCount + " New Watchlist Match" + (matchCount == 1 ? "" : "es"),
                    matchCount + " article" + (matchCount == 1 ? "" : "s") + " matched your watchlist items. Check your watchlist for details.",
                    "/watchlist"
            );
        } catch (Exception e) {
            log.warn("notifyWatchlistMatches() | webhook dispatch failed: {}", e.getMessage());
        }
        log.debug("notifyWatchlistMatches() | return=void");
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
