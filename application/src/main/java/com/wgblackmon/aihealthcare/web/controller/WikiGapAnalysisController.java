package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.KnowledgeCompilationPort;
import com.wgblackmon.aihealthcare.domain.service.WikiGapAnalysisService;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiGapItemEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiGapRunEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for triggering wiki gap analysis and compiling approved gaps.
 *
 * <p>Provides two manual trigger endpoints:
 * <ul>
 *   <li>{@code POST /monitoring/wiki/gap-analysis} — runs gap analysis on recent articles</li>
 *   <li>{@code POST /monitoring/wiki/gap-analysis/compile-approved} — compiles approved gaps into wiki pages</li>
 * </ul>
 *
 * <p>The path {@code /monitoring/**} is already {@code permitAll()} in SecurityConfig.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
@Slf4j
@RestController
public class WikiGapAnalysisController {

    private final WikiGapAnalysisService gapService;
    private final NewsArticleRepository articleRepository;
    private final WikiPageRepository pageRepository;
    private final KnowledgeCompilationPort compilationPort;

    public WikiGapAnalysisController(WikiGapAnalysisService gapService,
                                      NewsArticleRepository articleRepository,
                                      WikiPageRepository pageRepository,
                                      KnowledgeCompilationPort compilationPort) {
        log.debug("WikiGapAnalysisController() | gapService={}, articleRepository={}, pageRepository={}, compilationPort={}",
                gapService.getClass().getSimpleName(),
                articleRepository.getClass().getSimpleName(),
                pageRepository.getClass().getSimpleName(),
                compilationPort.getClass().getSimpleName());
        this.gapService = gapService;
        this.articleRepository = articleRepository;
        this.pageRepository = pageRepository;
        this.compilationPort = compilationPort;
    }

    /**
     * Triggers a wiki gap analysis using articles from the last 7 days.
     *
     * @return JSON run summary
     */
    @PostMapping("/monitoring/wiki/gap-analysis")
    public ResponseEntity<Map<String, Object>> triggerGapAnalysis() {
        log.debug("triggerGapAnalysis() | (no args)");

        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<NewsArticleEntity> entities = articleRepository.findByCreatedAtAfterOrderByCreatedAtAsc(since);
        List<NewsArticle> articles = new ArrayList<>();
        for (NewsArticleEntity entity : entities) {
            articles.add(mapToDomain(entity));
        }

        List<WikiPageEntity> pages = pageRepository.findAll();
        log.info("triggerGapAnalysis() | found {} articles from last 7 days, {} wiki pages",
                articles.size(), pages.size());

        WikiGapRunEntity run = gapService.runGapAnalysis(articles, pages);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.getId());
        result.put("status", run.getStatus());
        result.put("articlesAnalyzed", run.getArticlesAnalyzed());
        result.put("wikiPagesChecked", run.getWikiPagesChecked());
        result.put("gapsFound", run.getGapsFound());
        result.put("summary", run.getSummary());

        log.debug("triggerGapAnalysis() | return={}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * Compiles all approved gap items into wiki pages using the existing
     * wiki compilation pipeline.
     *
     * @return JSON compilation report
     */
    @PostMapping("/monitoring/wiki/gap-analysis/compile-approved")
    public ResponseEntity<Map<String, Object>> compileApprovedGaps() {
        log.debug("compileApprovedGaps() | (no args)");

        List<WikiGapItemEntity> approvedItems = gapService.getApprovedItems();
        if (approvedItems.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "NO_ITEMS");
            result.put("message", "No approved gap items to compile");
            log.debug("compileApprovedGaps() | return={}", result);
            return ResponseEntity.ok(result);
        }

        Set<String> allArticleIds = new LinkedHashSet<>();
        for (WikiGapItemEntity item : approvedItems) {
            List<String> ids = gapService.splitArticleIds(item.getArticleIds());
            allArticleIds.addAll(ids);
        }

        List<NewsArticleEntity> entities = articleRepository.findByArticleIdIn(new ArrayList<>(allArticleIds));
        List<NewsArticle> articles = new ArrayList<>();
        for (NewsArticleEntity entity : entities) {
            articles.add(mapToDomain(entity));
        }

        log.info("compileApprovedGaps() | compiling {} approved gaps with {} articles",
                approvedItems.size(), articles.size());

        CompilationReport report = compilationPort.compileNewSources(articles);

        for (WikiGapItemEntity item : approvedItems) {
            gapService.updateItemStatus(item.getId(), "COMPILED");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("gapsCompiled", approvedItems.size());
        result.put("articlesProcessed", report.articlesProcessed());
        result.put("pagesCreated", report.pagesCreated().size());
        result.put("pagesUpdated", report.pagesUpdated().size());

        log.debug("compileApprovedGaps() | return={}", result);
        return ResponseEntity.ok(result);
    }

    private NewsArticle mapToDomain(NewsArticleEntity entity) {
        log.debug("mapToDomain() | articleId={}", entity.getArticleId());

        URI url = null;
        if (entity.getUrl() != null && !entity.getUrl().isBlank()) {
            try {
                url = URI.create(entity.getUrl());
            } catch (IllegalArgumentException e) {
                log.warn("mapToDomain() | invalid URL '{}', using null", entity.getUrl());
            }
        }

        NewsArticle result = new NewsArticle(
                entity.getArticleId(),
                entity.getTitle(),
                url,
                entity.getBodyText(),
                entity.getTopic(),
                entity.getAuthor(),
                entity.getTopicId(),
                entity.getSourceName(),
                entity.getSourceTier(),
                entity.getSourceWeight(),
                entity.getPublishedAt()
        );

        log.debug("mapToDomain() | return={}", result.articleId());
        return result;
    }
}
