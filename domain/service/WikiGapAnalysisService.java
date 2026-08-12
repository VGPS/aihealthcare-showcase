package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.GapItem;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.WikiGapAnalysisResult;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiGapAnalysisPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiGapItemEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiGapItemRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiGapRunEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiGapRunRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Domain service orchestrating wiki gap analysis — identifies topics covered
 * by articles but missing from the wiki, persists results, and manages the
 * admin approval workflow.
 *
 * <p>This is a pure domain service (no Spring annotations); it is wired
 * in {@code AppConfig} following the existing pattern for {@code WikiLintService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
@Slf4j
public class WikiGapAnalysisService {

    private final WikiGapAnalysisPort gapAnalysisPort;
    private final WikiGapRunRepository runRepository;
    private final WikiGapItemRepository itemRepository;

    public WikiGapAnalysisService(WikiGapAnalysisPort gapAnalysisPort,
                                   WikiGapRunRepository runRepository,
                                   WikiGapItemRepository itemRepository) {
        log.debug("WikiGapAnalysisService() | gapAnalysisPort={}, runRepository={}, itemRepository={}",
                gapAnalysisPort.getClass().getSimpleName(),
                runRepository.getClass().getSimpleName(),
                itemRepository.getClass().getSimpleName());
        this.gapAnalysisPort = gapAnalysisPort;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        log.debug("WikiGapAnalysisService() | return=void");
    }

    /**
     * Runs a gap analysis comparing recent articles against wiki pages,
     * persists the run and items, and returns the run entity.
     *
     * @param articles      recent articles to analyze
     * @param existingPages current wiki pages
     * @return the persisted run entity
     */
    public WikiGapRunEntity runGapAnalysis(List<NewsArticle> articles,
                                            List<WikiPageEntity> existingPages) {
        log.debug("runGapAnalysis() | articles={}, pages={}", articles.size(), existingPages.size());

        Instant startedAt = Instant.now();
        WikiGapRunEntity run = new WikiGapRunEntity();
        run.setStartedAt(startedAt);
        run.setArticlesAnalyzed(articles.size());
        run.setWikiPagesChecked(existingPages.size());

        try {
            WikiGapAnalysisResult result = gapAnalysisPort.analyzeGaps(articles, existingPages);

            run.setCompletedAt(Instant.now());
            run.setGapsFound(result.gaps().size());
            run.setSummary(result.summary());
            run.setStatus("COMPLETED");
            run = runRepository.save(run);

            for (GapItem gap : result.gaps()) {
                WikiGapItemEntity item = new WikiGapItemEntity();
                item.setRunId(run.getId());
                item.setTopic(gap.topic());
                item.setArticleIds(joinPipeDelimited(gap.articleIds()));
                item.setRecommendation(gap.recommendation());
                item.setStatus("PENDING");
                itemRepository.save(item);
            }

            log.info("runGapAnalysis() | completed — gaps={}, summary length={}",
                    result.gaps().size(), result.summary().length());
        } catch (Exception e) {
            run.setCompletedAt(Instant.now());
            run.setGapsFound(0);
            run.setSummary("Analysis failed: " + e.getMessage());
            run.setStatus("FAILED");
            run = runRepository.save(run);
            log.error("runGapAnalysis() | failed: {}", e.getMessage(), e);
        }

        log.debug("runGapAnalysis() | return=WikiGapRunEntity[id={}, status={}]",
                run.getId(), run.getStatus());
        return run;
    }

    /**
     * Returns recent gap analysis runs, newest first.
     *
     * @return list of recent runs (max 20)
     */
    public List<WikiGapRunEntity> getRecentRuns() {
        log.debug("getRecentRuns()");
        List<WikiGapRunEntity> result = runRepository.findTop20ByOrderByStartedAtDesc();
        log.debug("getRecentRuns() | return={} runs", result.size());
        return result;
    }

    /**
     * Returns a specific run by ID.
     *
     * @param runId the run ID
     * @return the run, or empty if not found
     */
    public Optional<WikiGapRunEntity> getRunById(Long runId) {
        log.debug("getRunById() | runId={}", runId);
        Optional<WikiGapRunEntity> result = runRepository.findById(runId);
        log.debug("getRunById() | return=present={}", result.isPresent());
        return result;
    }

    /**
     * Returns all gap items for a specific run.
     *
     * @param runId the run ID
     * @return list of gap items
     */
    public List<WikiGapItemEntity> getItemsByRunId(Long runId) {
        log.debug("getItemsByRunId() | runId={}", runId);
        List<WikiGapItemEntity> result = itemRepository.findByRunIdOrderByIdAsc(runId);
        log.debug("getItemsByRunId() | return={} items", result.size());
        return result;
    }

    /**
     * Returns all gap items with PENDING status.
     *
     * @return list of pending gap items
     */
    public List<WikiGapItemEntity> getPendingItems() {
        log.debug("getPendingItems()");
        List<WikiGapItemEntity> result = itemRepository.findByStatus("PENDING");
        log.debug("getPendingItems() | return={} items", result.size());
        return result;
    }

    /**
     * Returns all gap items with APPROVED status.
     *
     * @return list of approved gap items
     */
    public List<WikiGapItemEntity> getApprovedItems() {
        log.debug("getApprovedItems()");
        List<WikiGapItemEntity> result = itemRepository.findByStatus("APPROVED");
        log.debug("getApprovedItems() | return={} items", result.size());
        return result;
    }

    /**
     * Updates a gap item's status (APPROVED or DISMISSED).
     *
     * @param itemId the gap item ID
     * @param status the new status
     */
    public void updateItemStatus(Long itemId, String status) {
        log.debug("updateItemStatus() | itemId={}, status={}", itemId, status);

        Optional<WikiGapItemEntity> opt = itemRepository.findById(itemId);
        if (opt.isEmpty()) {
            log.warn("updateItemStatus() | item not found: {}", itemId);
            log.debug("updateItemStatus() | return=void");
            return;
        }

        WikiGapItemEntity item = opt.get();
        item.setStatus(status);
        item.setReviewedAt(Instant.now());
        itemRepository.save(item);

        log.debug("updateItemStatus() | return=void");
    }

    /**
     * Splits pipe-delimited article IDs into a list.
     *
     * @param pipeDelimited pipe-delimited string
     * @return list of article IDs
     */
    public List<String> splitArticleIds(String pipeDelimited) {
        log.debug("splitArticleIds() | input={}", pipeDelimited);
        List<String> result = new ArrayList<>();
        if (pipeDelimited == null || pipeDelimited.isBlank()) {
            log.debug("splitArticleIds() | return=0 ids");
            return result;
        }
        String[] parts = pipeDelimited.split("\\|");
        for (String part : parts) {
            String id = part.trim();
            if (!id.isEmpty()) {
                result.add(id);
            }
        }
        log.debug("splitArticleIds() | return={} ids", result.size());
        return result;
    }

    private String joinPipeDelimited(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append("|");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }
}
