package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.SearchArticlesUseCase;
import com.wgblackmon.aihealthcare.web.dto.ArticleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * REST controller exposing the multi-field article search endpoint.
 *
 * <p>Provides {@code GET /api/v1/articles/search} with optional query parameters
 * for each searchable field. All parameters are optional — only non-null values
 * become AND-combined predicates. Text fields use case-insensitive substring
 * matching; {@code sourceTier} uses exact match; date parameters define inclusive
 * range bounds.
 *
 * <p>This controller depends only on the {@link SearchArticlesUseCase} inbound
 * port — it never references infrastructure or persistence classes directly.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-01
 * @updated 2026-06-01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class ArticleSearchController {

    private final SearchArticlesUseCase searchUseCase;

    public ArticleSearchController(SearchArticlesUseCase searchUseCase) {
        log.debug("ArticleSearchController() | searchUseCase={}", searchUseCase.getClass().getSimpleName());
        this.searchUseCase = searchUseCase;
    }

    /**
     * Searches persisted articles using optional filter criteria.
     *
     * @param title         substring match against title (case-insensitive)
     * @param topic         substring match against topic (case-insensitive)
     * @param author        substring match against author (case-insensitive)
     * @param sourceName    substring match against source name (case-insensitive)
     * @param sourceTier    exact match against source tier
     * @param bodyText      substring match against body text (case-insensitive)
     * @param publishedFrom inclusive lower bound on publishedAt (ISO-8601)
     * @param publishedTo   inclusive upper bound on publishedAt (ISO-8601)
     * @param createdFrom   inclusive lower bound on createdAt (ISO-8601)
     * @param createdTo     inclusive upper bound on createdAt (ISO-8601)
     * @return 200 OK with a list of matching {@link ArticleResponse} DTOs
     */
    @GetMapping("/articles/search")
    public ResponseEntity<List<ArticleResponse>> searchArticles(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false) String sourceTier,
            @RequestParam(required = false) String bodyText,
            @RequestParam(required = false) Instant publishedFrom,
            @RequestParam(required = false) Instant publishedTo,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo) {
        log.debug("searchArticles() | title={}, topic={}, author={}, sourceName={}, sourceTier={}, "
                + "bodyText={}, publishedFrom={}, publishedTo={}, createdFrom={}, createdTo={}",
                title, topic, author, sourceName, sourceTier, bodyText,
                publishedFrom, publishedTo, createdFrom, createdTo);

        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                title, topic, author, sourceName, sourceTier, bodyText,
                publishedFrom, publishedTo, createdFrom, createdTo);

        List<NewsArticle> articles = searchUseCase.search(criteria);

        List<ArticleResponse> responses = new ArrayList<>();
        for (NewsArticle article : articles) {
            responses.add(new ArticleResponse(
                    article.articleId(),
                    article.title(),
                    article.url() != null ? article.url().toString() : "",
                    article.topic(),
                    article.author(),
                    article.sourceName(),
                    article.sourceTier(),
                    article.sourceWeight(),
                    article.publishedAt()
            ));
        }

        log.debug("searchArticles() | return={} articles", responses.size());
        return ResponseEntity.ok(responses);
    }
}
