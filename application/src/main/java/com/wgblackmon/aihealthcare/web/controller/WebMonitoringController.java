package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.service.PerplexityCompanyDiscoveryService;
import com.wgblackmon.aihealthcare.domain.service.TopicSummaryGenerationService;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import com.wgblackmon.aihealthcare.infrastructure.ai.EmbeddingScheduler;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.huggingface.HuggingFaceHarvester;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.web.WebPageHarvester;
import com.wgblackmon.aihealthcare.infrastructure.persistence.PageContentHashEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.PageContentHashRepository;
import com.wgblackmon.aihealthcare.web.dto.HarvestResultResponse;
import com.wgblackmon.aihealthcare.web.dto.HuggingFaceHarvestResponse;
import com.wgblackmon.aihealthcare.web.dto.PageHashResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for manually triggering web monitoring harvests and
 * inspecting stored page content hashes.
 *
 * <p>Provides on-demand triggers for the scheduled competitor page and
 * HuggingFace model discovery jobs, useful for testing and ad-hoc updates.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-04-19
 * @updated 2026-08-02
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/monitoring")
public class WebMonitoringController {

    private final WebPageHarvester webPageHarvester;
    private final HuggingFaceHarvester huggingFaceHarvester;
    private final ArticleStoragePort articleStoragePort;
    private final PageContentHashRepository hashRepository;
    private final ArticleHarvestingPort articleHarvestingPort;
    private final TopicSummaryGenerationService topicSummaryService;
    private final NewsTopicProperties newsTopicProperties;
    private final EmbeddingScheduler embeddingScheduler;
    private final PerplexityCompanyDiscoveryService companyDiscoveryService;

    public WebMonitoringController(WebPageHarvester webPageHarvester,
                                   HuggingFaceHarvester huggingFaceHarvester,
                                   ArticleStoragePort articleStoragePort,
                                   PageContentHashRepository hashRepository,
                                   ArticleHarvestingPort articleHarvestingPort,
                                   TopicSummaryGenerationService topicSummaryService,
                                   NewsTopicProperties newsTopicProperties,
                                   EmbeddingScheduler embeddingScheduler,
                                   PerplexityCompanyDiscoveryService companyDiscoveryService) {
        log.debug("WebMonitoringController() | webPageHarvester={}, huggingFaceHarvester={}, " +
                  "articleStoragePort={}, hashRepository={}",
                  webPageHarvester.getClass().getSimpleName(),
                  huggingFaceHarvester.getClass().getSimpleName(),
                  articleStoragePort.getClass().getSimpleName(),
                  hashRepository.getClass().getSimpleName());
        this.webPageHarvester = webPageHarvester;
        this.huggingFaceHarvester = huggingFaceHarvester;
        this.articleStoragePort = articleStoragePort;
        this.hashRepository = hashRepository;
        this.articleHarvestingPort = articleHarvestingPort;
        this.topicSummaryService = topicSummaryService;
        this.newsTopicProperties = newsTopicProperties;
        this.embeddingScheduler = embeddingScheduler;
        this.companyDiscoveryService = companyDiscoveryService;
    }

    /**
     * Manually triggers a competitor page harvest.
     *
     * @return harvest result with page count and changes detected
     */
    @PostMapping("/harvest")
    public ResponseEntity<HarvestResultResponse> triggerCompetitorHarvest() {
        log.debug("triggerCompetitorHarvest() | (no args)");

        List<NewsArticle> changed = webPageHarvester.harvestChangedPages();
        if (!changed.isEmpty()) {
            articleStoragePort.save(changed);
        }

        int totalPages = (int) hashRepository.count() + changed.size();
        HarvestResultResponse response = new HarvestResultResponse(
                Math.max(totalPages, changed.size()), changed.size());

        log.info("triggerCompetitorHarvest() | checked pages, {} changes detected",
                 changed.size());
        log.debug("triggerCompetitorHarvest() | return={}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * Manually triggers a HuggingFace model discovery harvest.
     *
     * @return harvest result with number of models discovered
     */
    @PostMapping("/huggingface")
    public ResponseEntity<HuggingFaceHarvestResponse> triggerHuggingFaceHarvest() {
        log.debug("triggerHuggingFaceHarvest() | (no args)");

        List<NewsArticle> models = huggingFaceHarvester.harvestModels();
        if (!models.isEmpty()) {
            articleStoragePort.save(models);
        }

        HuggingFaceHarvestResponse response = new HuggingFaceHarvestResponse(models.size());

        log.info("triggerHuggingFaceHarvest() | {} models discovered", models.size());
        log.debug("triggerHuggingFaceHarvest() | return={}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * Manually triggers a full RSS feed harvest across all tiers and persists results.
     *
     * @return harvest result with total article count saved
     */
    @PostMapping("/feeds")
    public ResponseEntity<HarvestResultResponse> triggerFeedHarvest() {
        log.debug("triggerFeedHarvest() | (no args)");

        List<NewsArticle> all = articleHarvestingPort.harvestAll();
        if (!all.isEmpty()) {
            articleStoragePort.save(all);
        }

        HarvestResultResponse response = new HarvestResultResponse(all.size(), all.size());
        log.info("triggerFeedHarvest() | harvested and saved {} articles", all.size());
        log.debug("triggerFeedHarvest() | return={}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * Manually triggers AI topic summary generation for all configured topics.
     *
     * @return simple text confirmation
     */
    @PostMapping("/summaries")
    public ResponseEntity<String> triggerTopicSummaries() {
        log.debug("triggerTopicSummaries() | (no args)");

        List<String> topics = newsTopicProperties.getTopics();
        topicSummaryService.generateSummaries(topics);

        String result = "Generated summaries for " + topics.size() + " topics";
        log.info("triggerTopicSummaries() | {}", result);
        log.debug("triggerTopicSummaries() | return={}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * Manually triggers article embedding into the vector store.
     *
     * <p>Delegates to {@link EmbeddingScheduler#embedArticles()} to embed all
     * persisted articles.  Useful for populating the vector store on-demand
     * without waiting for the scheduled cron.
     *
     * @return simple text confirmation with the result
     */
    @PostMapping("/embeddings")
    public ResponseEntity<String> triggerEmbedding() {
        log.debug("triggerEmbedding() | (no args)");

        embeddingScheduler.embedArticles();

        String result = "Embedding run completed";
        log.info("triggerEmbedding() | {}", result);
        log.debug("triggerEmbedding() | return={}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * Manually triggers Perplexity-powered company discovery pipeline.
     *
     * @return JSON result with number of companies discovered
     */
    @PostMapping("/company-discovery")
    public ResponseEntity<Map<String, Object>> triggerCompanyDiscovery() {
        log.debug("triggerCompanyDiscovery() | (no args)");

        int persisted = companyDiscoveryService.runDiscoveryCycle();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companiesDiscovered", persisted);
        log.info("triggerCompanyDiscovery() | {} new companies persisted", persisted);
        log.debug("triggerCompanyDiscovery() | return={}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * Lists all stored page content hashes for diagnostic purposes.
     *
     * @return list of page hash entries
     */
    @GetMapping("/hashes")
    public ResponseEntity<List<PageHashResponse>> listPageHashes() {
        log.debug("listPageHashes() | (no args)");

        List<PageContentHashEntity> entities = hashRepository.findAll();
        List<PageHashResponse> responses = new ArrayList<>();
        for (PageContentHashEntity entity : entities) {
            responses.add(new PageHashResponse(
                    entity.getPageUrl(),
                    entity.getContentHash(),
                    entity.getLastCheckedAt()));
        }

        log.debug("listPageHashes() | return={} entries", responses.size());
        return ResponseEntity.ok(responses);
    }
}
