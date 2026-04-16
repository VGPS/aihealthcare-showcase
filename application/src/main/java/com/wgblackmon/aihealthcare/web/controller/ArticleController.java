package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.web.dto.ArticleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller exposing the article query endpoint.
 *
 * <p>Provides {@code GET /api/v1/articles?topic={topic}&limit={limit}} to allow
 * callers to confirm that the RSS feed harvester is persisting articles to the
 * database correctly.  The controller delegates to
 * {@link ArticleIngestionPort#fetchArticles} which is backed by the JPA adapter
 * in Slice 2a.
 *
 * <p>This controller never references the JPA adapter, repository, or any
 * infrastructure class directly — it depends only on the domain port interface.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class ArticleController {

    private final ArticleIngestionPort ingestionPort;

    public ArticleController(ArticleIngestionPort ingestionPort) {
        log.debug("ArticleController() | ingestionPort={}", ingestionPort.getClass().getSimpleName());
        this.ingestionPort = ingestionPort;
    }

    /**
     * Returns persisted articles matching the given topic name.
     *
     * @param topic the topic name to filter by (e.g. "PubMed AI Healthcare")
     * @param limit maximum number of articles to return (default 20)
     * @return 200 OK with a list of {@link ArticleResponse} DTOs
     */
    @GetMapping("/articles")
    public ResponseEntity<List<ArticleResponse>> listArticles(
            @RequestParam String topic,
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("listArticles() | topic={}, limit={}", topic, limit);

        List<NewsArticle> articles = ingestionPort.fetchArticles(topic, limit);
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

        log.debug("listArticles() | return={} articles", responses.size());
        return ResponseEntity.ok(responses);
    }
}
