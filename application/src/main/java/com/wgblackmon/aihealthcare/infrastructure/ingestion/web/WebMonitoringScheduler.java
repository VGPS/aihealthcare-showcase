package com.wgblackmon.aihealthcare.infrastructure.ingestion.web;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.huggingface.HuggingFaceHarvester;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled driver for web page monitoring and HuggingFace model discovery.
 *
 * <p>Runs on a separate schedule from the RSS-based
 * {@link com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedHarvestScheduler}
 * to avoid Spring bean injection ambiguity and maintain clean separation of
 * concerns between feed-based and web-scraping-based ingestion.
 *
 * <p>Scheduling cadences (configurable via {@code application.yml}):
 * <ul>
 *   <li><b>Competitor pages</b> — daily via {@link #harvestCompetitorPages()}</li>
 *   <li><b>HuggingFace models</b> — daily via {@link #harvestHuggingFaceModels()}</li>
 * </ul>
 *
 * <p>All harvested content flows into the standard {@link ArticleStoragePort}
 * pipeline, which handles URL deduplication automatically.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-05-22
 */
@Slf4j
@Component
public class WebMonitoringScheduler {

    private final WebPageHarvester webPageHarvester;
    private final HuggingFaceHarvester huggingFaceHarvester;
    private final ArticleStoragePort articleStoragePort;

    public WebMonitoringScheduler(WebPageHarvester webPageHarvester,
                                  HuggingFaceHarvester huggingFaceHarvester,
                                  ArticleStoragePort articleStoragePort) {
        log.debug("WebMonitoringScheduler() | webPageHarvester={}, huggingFaceHarvester={}, articleStoragePort={}",
                  webPageHarvester.getClass().getSimpleName(),
                  huggingFaceHarvester.getClass().getSimpleName(),
                  articleStoragePort.getClass().getSimpleName());
        this.webPageHarvester = webPageHarvester;
        this.huggingFaceHarvester = huggingFaceHarvester;
        this.articleStoragePort = articleStoragePort;
    }

    /**
     * Daily harvest of competitor web pages.
     * Cron configured via {@code aihealthcare.harvest.competitor-cron} (default: 05:00 UTC).
     */
    @Scheduled(cron = "${aihealthcare.harvest.competitor-cron}", zone = "UTC")
    public void harvestCompetitorPages() {
        log.debug("harvestCompetitorPages() | starting daily COMPETITOR harvest");
        try {
            List<NewsArticle> articles = webPageHarvester.harvestChangedPages();
            if (!articles.isEmpty()) {
                articleStoragePort.save(articles);
                log.info("harvestCompetitorPages() | saved {} changed competitor pages",
                         articles.size());
            } else {
                log.info("harvestCompetitorPages() | no competitor page changes detected");
            }
        } catch (Exception ex) {
            log.error("harvestCompetitorPages() | harvest failed: {}", ex.getMessage(), ex);
        }
        log.debug("harvestCompetitorPages() | return=void");
    }

    /**
     * Daily harvest of HuggingFace healthcare-related models.
     * Cron configured via {@code aihealthcare.harvest.huggingface-cron} (default: 05:30 UTC).
     */
    @Scheduled(cron = "${aihealthcare.harvest.huggingface-cron}", zone = "UTC")
    public void harvestHuggingFaceModels() {
        log.debug("harvestHuggingFaceModels() | starting daily HUGGINGFACE harvest");
        try {
            List<NewsArticle> articles = huggingFaceHarvester.harvestModels();
            if (!articles.isEmpty()) {
                articleStoragePort.save(articles);
                log.info("harvestHuggingFaceModels() | saved {} HuggingFace models",
                         articles.size());
            } else {
                log.info("harvestHuggingFaceModels() | no new HuggingFace models discovered");
            }
        } catch (Exception ex) {
            log.error("harvestHuggingFaceModels() | harvest failed: {}", ex.getMessage(), ex);
        }
        log.debug("harvestHuggingFaceModels() | return=void");
    }

    /**
     * Returns the web page harvester for use by the manual-trigger controller.
     *
     * @return the web page harvester instance
     */
    public WebPageHarvester getWebPageHarvester() {
        return webPageHarvester;
    }

    /**
     * Returns the HuggingFace harvester for use by the manual-trigger controller.
     *
     * @return the HuggingFace harvester instance
     */
    public HuggingFaceHarvester getHuggingFaceHarvester() {
        return huggingFaceHarvester;
    }
}
