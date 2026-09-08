package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.pubmed.BackfillProperties;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.pubmed.PubMedBackfillHarvester;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineAsyncRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for triggering PubMed historical backfill harvests.
 *
 * <p>Queries the PubMed E-utilities API for healthcare AI articles within a
 * configurable date range, persists them via {@link ArticleStoragePort}, and
 * returns a summary of articles harvested per query.
 *
 * <p>Default queries cover AI legal liability, regulation, and compliance in
 * healthcare — the user's primary area of interest for wiki compilation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-09-08
 */
@Slf4j
@RestController
@RequestMapping("/monitoring/backfill")
public class BackfillController {

    private final PubMedBackfillHarvester pubMedHarvester;
    private final ArticleStoragePort articleStoragePort;
    private final BackfillProperties backfillProperties;
    private final PipelineAsyncRunner asyncRunner;

    public BackfillController(PubMedBackfillHarvester pubMedHarvester,
                              ArticleStoragePort articleStoragePort,
                              BackfillProperties backfillProperties,
                              PipelineAsyncRunner asyncRunner) {
        log.debug("BackfillController() | pubMedHarvester={}, articleStoragePort={}, backfillProperties={}, asyncRunner={}",
                  pubMedHarvester.getClass().getSimpleName(),
                  articleStoragePort.getClass().getSimpleName(),
                  backfillProperties.getClass().getSimpleName(),
                  asyncRunner.getClass().getSimpleName());
        this.pubMedHarvester = pubMedHarvester;
        this.articleStoragePort = articleStoragePort;
        this.backfillProperties = backfillProperties;
        this.asyncRunner = asyncRunner;
    }

    /**
     * Triggers a PubMed backfill harvest using default queries.
     *
     * <p>Searches PubMed for healthcare AI articles published between
     * {@code fromYear} and {@code toYear} (default: 2022–2025), persists
     * results via {@link ArticleStoragePort}, and returns per-query counts.
     *
     * @param fromYear  start year (default 2022)
     * @param toYear    end year (default 2025)
     * @param maxPerQuery maximum articles per query (default 50)
     * @return backfill result with per-query article counts
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> triggerBackfill(
            @RequestParam(required = false) Integer fromYear,
            @RequestParam(required = false) Integer toYear,
            @RequestParam(required = false) Integer maxPerQuery) {
        int effectiveFromYear = (fromYear != null) ? fromYear : backfillProperties.getFromYear();
        int effectiveToYear = (toYear != null) ? toYear : backfillProperties.getToYear();
        int effectiveMaxPerQuery = (maxPerQuery != null) ? maxPerQuery : backfillProperties.getMaxPerQuery();
        log.debug("triggerBackfill() | fromYear={}, toYear={}, maxPerQuery={}",
                  effectiveFromYear, effectiveToYear, effectiveMaxPerQuery);

        return asyncRunner.runAsync("pubmed-backfill", () -> {
            LocalDate from = LocalDate.of(effectiveFromYear, 1, 1);
            LocalDate to = LocalDate.of(effectiveToYear, 12, 31);

            List<String> queries = backfillProperties.getQueries();
            int totalArticles = 0;

            for (String query : queries) {
                log.info("triggerBackfill() | running query: {}", query);
                List<NewsArticle> articles = pubMedHarvester.harvest(query, from, to, effectiveMaxPerQuery);
                if (!articles.isEmpty()) {
                    articleStoragePort.save(articles);
                }
                totalArticles += articles.size();
                log.info("triggerBackfill() | query '{}' yielded {} articles", query, articles.size());
            }

            log.info("triggerBackfill() | backfill complete: {} queries, {} total articles",
                     queries.size(), totalArticles);
        });
    }

    /**
     * Triggers a PubMed backfill harvest with a custom query.
     *
     * @param query       PubMed search term
     * @param fromYear    start year (default 2022)
     * @param toYear      end year (default 2025)
     * @param maxResults  maximum articles to retrieve (default 100)
     * @return backfill result
     */
    @PostMapping("/custom")
    public ResponseEntity<Map<String, Object>> triggerCustomBackfill(
            @RequestParam String query,
            @RequestParam(defaultValue = "2022") int fromYear,
            @RequestParam(defaultValue = "2025") int toYear,
            @RequestParam(defaultValue = "100") int maxResults) {
        log.debug("triggerCustomBackfill() | query={}, fromYear={}, toYear={}, maxResults={}",
                  query, fromYear, toYear, maxResults);

        return asyncRunner.runAsync("pubmed-backfill", () -> {
            LocalDate from = LocalDate.of(fromYear, 1, 1);
            LocalDate to = LocalDate.of(toYear, 12, 31);

            List<NewsArticle> articles = pubMedHarvester.harvest(query, from, to, maxResults);
            if (!articles.isEmpty()) {
                articleStoragePort.save(articles);
            }
            log.info("triggerCustomBackfill() | query '{}' yielded {} articles", query, articles.size());
        });
    }
}
