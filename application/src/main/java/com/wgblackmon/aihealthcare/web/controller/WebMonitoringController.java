package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
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
import java.util.List;

/**
 * REST controller for manually triggering web monitoring harvests and
 * inspecting stored page content hashes.
 *
 * <p>Provides on-demand triggers for the scheduled competitor page and
 * HuggingFace model discovery jobs, useful for testing and ad-hoc updates.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/monitoring")
public class WebMonitoringController {

    private final WebPageHarvester webPageHarvester;
    private final HuggingFaceHarvester huggingFaceHarvester;
    private final ArticleStoragePort articleStoragePort;
    private final PageContentHashRepository hashRepository;

    public WebMonitoringController(WebPageHarvester webPageHarvester,
                                   HuggingFaceHarvester huggingFaceHarvester,
                                   ArticleStoragePort articleStoragePort,
                                   PageContentHashRepository hashRepository) {
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
