package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.pubmed.BackfillProperties;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.pubmed.PubMedBackfillHarvester;
import com.wgblackmon.aihealthcare.web.dto.BackfillResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
 * @updated 2026-07-04
 */
@Slf4j
@RestController
@RequestMapping("/monitoring/backfill")
public class BackfillController {

    private final PubMedBackfillHarvester pubMedHarvester;
    private final ArticleStoragePort articleStoragePort;
    private final BackfillProperties backfillProperties;

    public BackfillController(PubMedBackfillHarvester pubMedHarvester,
                              ArticleStoragePort articleStoragePort,
                              BackfillProperties backfillProperties) {
        log.debug("BackfillController() | pubMedHarvester={}, articleStoragePort={}, backfillProperties={}",
                  pubMedHarvester.getClass().getSimpleName(),
                  articleStoragePort.getClass().getSimpleName(),
                  backfillProperties.getClass().getSimpleName());
        this.pubMedHarvester = pubMedHarvester;
        this.articleStoragePort = articleStoragePort;
        this.backfillProperties = backfillProperties;
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
    public ResponseEntity<BackfillResultResponse> triggerBackfill(
            @RequestParam(required = false) Integer fromYear,
            @RequestParam(required = false) Integer toYear,
            @RequestParam(required = false) Integer maxPerQuery) {
        int effectiveFromYear = (fromYear != null) ? fromYear : backfillProperties.getFromYear();
        int effectiveToYear = (toYear != null) ? toYear : backfillProperties.getToYear();
        int effectiveMaxPerQuery = (maxPerQuery != null) ? maxPerQuery : backfillProperties.getMaxPerQuery();
        log.debug("triggerBackfill() | fromYear={}, toYear={}, maxPerQuery={}",
                  effectiveFromYear, effectiveToYear, effectiveMaxPerQuery);

        LocalDate from = LocalDate.of(effectiveFromYear, 1, 1);
        LocalDate to = LocalDate.of(effectiveToYear, 12, 31);

        List<String> queries = backfillProperties.getQueries();
        List<BackfillResultResponse.QueryResult> queryResults = new ArrayList<>();
        int totalArticles = 0;

        for (String query : queries) {
            log.info("triggerBackfill() | running query: {}", query);

            List<NewsArticle> articles = pubMedHarvester.harvest(query, from, to, effectiveMaxPerQuery);

            if (!articles.isEmpty()) {
                articleStoragePort.save(articles);
            }

            queryResults.add(new BackfillResultResponse.QueryResult(query, articles.size()));
            totalArticles += articles.size();

            log.info("triggerBackfill() | query '{}' yielded {} articles", query, articles.size());
        }

        BackfillResultResponse response = new BackfillResultResponse(
                queries.size(), totalArticles, queryResults);

        log.info("triggerBackfill() | backfill complete: {} queries, {} total articles",
                 queries.size(), totalArticles);
        log.debug("triggerBackfill() | return={}", response);
        return ResponseEntity.ok(response);
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
    public ResponseEntity<BackfillResultResponse> triggerCustomBackfill(
            @RequestParam String query,
            @RequestParam(defaultValue = "2022") int fromYear,
            @RequestParam(defaultValue = "2025") int toYear,
            @RequestParam(defaultValue = "100") int maxResults) {
        log.debug("triggerCustomBackfill() | query={}, fromYear={}, toYear={}, maxResults={}",
                  query, fromYear, toYear, maxResults);

        LocalDate from = LocalDate.of(fromYear, 1, 1);
        LocalDate to = LocalDate.of(toYear, 12, 31);

        List<NewsArticle> articles = pubMedHarvester.harvest(query, from, to, maxResults);

        if (!articles.isEmpty()) {
            articleStoragePort.save(articles);
        }

        List<BackfillResultResponse.QueryResult> queryResults = new ArrayList<>();
        queryResults.add(new BackfillResultResponse.QueryResult(query, articles.size()));

        BackfillResultResponse response = new BackfillResultResponse(1, articles.size(), queryResults);

        log.info("triggerCustomBackfill() | query '{}' yielded {} articles", query, articles.size());
        log.debug("triggerCustomBackfill() | return={}", response);
        return ResponseEntity.ok(response);
    }
}
