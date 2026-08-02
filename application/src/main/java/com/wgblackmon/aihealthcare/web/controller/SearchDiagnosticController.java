package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.SearchArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import com.wgblackmon.aihealthcare.web.dto.SearchDiagnosticResponse;
import com.wgblackmon.aihealthcare.web.dto.SearchDiagnosticResponse.ArticleHit;
import com.wgblackmon.aihealthcare.web.dto.SearchDiagnosticResponse.SearchResult;
import com.wgblackmon.aihealthcare.web.dto.SearchDiagnosticResponse.SnapshotResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Diagnostic REST endpoint that compares three article search strategies
 * side by side: trend snapshot lookup, semantic (vector) search, and
 * text (LIKE substring) search.
 *
 * <p>Intended for debugging cross-data consistency between the Trends page
 * and the Article Search page.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-01
 * @updated 2026-08-01
 */
@Slf4j
@RestController
public class SearchDiagnosticController {

    private final TrendSnapshotPort trendSnapshotPort;
    private final ArticleSearchPort articleSearchPort;
    private final SearchArticlesUseCase searchArticlesUseCase;

    public SearchDiagnosticController(TrendSnapshotPort trendSnapshotPort,
                                      ArticleSearchPort articleSearchPort,
                                      SearchArticlesUseCase searchArticlesUseCase) {
        log.debug("SearchDiagnosticController() | trendSnapshotPort={}, articleSearchPort={}, searchArticlesUseCase={}",
                  trendSnapshotPort.getClass().getSimpleName(),
                  articleSearchPort.getClass().getSimpleName(),
                  searchArticlesUseCase.getClass().getSimpleName());
        this.trendSnapshotPort = trendSnapshotPort;
        this.articleSearchPort = articleSearchPort;
        this.searchArticlesUseCase = searchArticlesUseCase;
    }

    /**
     * Runs three search strategies against the same query and returns results.
     *
     * @param query the article title or keyword to search for
     * @param topK  max results for semantic search (default 10)
     * @return diagnostic response comparing all three strategies
     */
    @GetMapping("/api/v1/search/diagnostic")
    public ResponseEntity<SearchDiagnosticResponse> diagnose(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK) {
        log.debug("diagnose() | query={}, topK={}", query, topK);

        // 1. Trend Snapshot scan
        SnapshotResult snapshotResult = scanTrendSnapshot(query);

        // 2. Semantic (vector) search
        SearchResult semanticResult = runSemanticSearch(query, topK);

        // 3. Text (LIKE) search
        SearchResult textResult = runTextSearch(query);

        SearchDiagnosticResponse response = new SearchDiagnosticResponse(
                query, topK, snapshotResult, semanticResult, textResult);

        log.debug("diagnose() | return=snapshot:{}, semantic:{}, text:{}",
                  snapshotResult.found(), semanticResult.resultCount(), textResult.resultCount());
        return ResponseEntity.ok(response);
    }

    private SnapshotResult scanTrendSnapshot(String query) {
        log.debug("scanTrendSnapshot() | query={}", query);

        Optional<TrendSnapshot> latest = trendSnapshotPort.findLatest();
        if (latest.isEmpty()) {
            log.debug("scanTrendSnapshot() | return=no snapshot");
            return new SnapshotResult(false, null, List.of());
        }

        TrendSnapshot snapshot = latest.get();
        String queryLower = query.toLowerCase();
        List<ArticleHit> matches = new ArrayList<>();
        String matchedKeyword = null;

        // Scan all topic lists
        List<List<TrendSignal>> allLists = List.of(
                snapshot.risingTopics(), snapshot.fadingTopics(), snapshot.newTopics());

        int rank = 0;
        for (List<TrendSignal> signals : allLists) {
            for (TrendSignal signal : signals) {
                for (ScoredArticle article : signal.topArticles()) {
                    if (article.title().toLowerCase().contains(queryLower)
                            || queryLower.contains(article.title().toLowerCase())) {
                        rank++;
                        matches.add(new ArticleHit(
                                rank,
                                article.articleId(),
                                article.title(),
                                article.keyword(),
                                article.sourceName(),
                                article.url()));
                        if (matchedKeyword == null) {
                            matchedKeyword = signal.keyword();
                        }
                    }
                }
            }
        }

        SnapshotResult result = new SnapshotResult(!matches.isEmpty(), matchedKeyword, matches);
        log.debug("scanTrendSnapshot() | return=found:{}, matches:{}", result.found(), matches.size());
        return result;
    }

    private SearchResult runSemanticSearch(String query, int topK) {
        log.debug("runSemanticSearch() | query={}, topK={}", query, topK);

        List<NewsArticle> articles = articleSearchPort.findSimilar(query, topK);
        List<ArticleHit> hits = new ArrayList<>();
        int rank = 0;
        for (NewsArticle article : articles) {
            rank++;
            hits.add(new ArticleHit(
                    rank,
                    article.articleId(),
                    article.title(),
                    article.topic(),
                    article.sourceName(),
                    article.url() != null ? article.url().toString() : null));
        }

        SearchResult result = new SearchResult(hits.size(), hits);
        log.debug("runSemanticSearch() | return={} results", result.resultCount());
        return result;
    }

    private SearchResult runTextSearch(String query) {
        log.debug("runTextSearch() | query={}", query);

        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                query, null, null, null, null, null, null);
        List<NewsArticle> articles = searchArticlesUseCase.search(criteria);
        List<ArticleHit> hits = new ArrayList<>();
        int rank = 0;
        for (NewsArticle article : articles) {
            rank++;
            hits.add(new ArticleHit(
                    rank,
                    article.articleId(),
                    article.title(),
                    article.topic(),
                    article.sourceName(),
                    article.url() != null ? article.url().toString() : null));
        }

        SearchResult result = new SearchResult(hits.size(), hits);
        log.debug("runTextSearch() | return={} results", result.resultCount());
        return result;
    }
}
