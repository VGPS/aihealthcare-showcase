package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.web.dto.ArticleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller exposing the article query endpoint.
 *
 * <p>Provides {@code GET /api/v1/articles?topic={topic}&limit={limit}&daysBack={daysBack}}
 * to allow callers to query harvested articles.  The {@code topic} parameter
 * uses case-insensitive substring matching (e.g. "health" matches
 * "Anthropic Healthcare AI" and "HuggingFace Healthcare LLMs").
 *
 * <p>The {@code daysBack} parameter is optional; when omitted the adapter
 * uses its configured default from {@code aihealthcare.articles.days-back}.
 *
 * <p>This controller never references the JPA adapter, repository, or any
 * infrastructure class directly — it depends only on the domain port interface.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-04-11
 * @updated 2026-09-11
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class ArticleController {

    private final ArticleIngestionPort ingestionPort;
    private final TierResolver tierResolver;
    private final TierGatingService tierGatingService;

    public ArticleController(ArticleIngestionPort ingestionPort,
                             TierResolver tierResolver,
                             TierGatingService tierGatingService) {
        log.debug("ArticleController() | ingestionPort={}", ingestionPort.getClass().getSimpleName());
        this.ingestionPort    = ingestionPort;
        this.tierResolver     = tierResolver;
        this.tierGatingService = tierGatingService;
    }

    /**
     * Returns persisted articles matching the given topic keyword.
     *
     * <p>Topic matching is case-insensitive substring search — "health"
     * matches "Anthropic Healthcare AI", "Google Health AI", etc.
     *
     * @param topic   keyword to match against article topics (case-insensitive contains)
     * @param limit   maximum number of articles to return (default 20)
     * @return 200 OK with a list of {@link ArticleResponse} DTOs
     */
    @GetMapping("/articles")
    public ResponseEntity<List<ArticleResponse>> listArticles(
            @RequestParam String topic,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "X-Subscriber-Email", required = false) String subscriberEmail) {
        log.debug("listArticles() | topic={}, limit={}, subscriberEmail={}", topic, limit, subscriberEmail);

        SubscriptionTier tier = tierResolver.resolveTier(subscriberEmail);
        int archiveDays = tierGatingService.archiveDaysFor(tier);
        log.debug("listArticles() | resolved tier={}, archiveDays={}", tier, archiveDays);

        List<NewsArticle> articles;
        if (archiveDays > 0) {
            List<NewsArticle> filtered = ingestionPort.fetchByTopicWithArchiveLimit(topic, archiveDays);
            articles = new ArrayList<>();
            int cap = Math.min(filtered.size(), limit);
            for (int i = 0; i < cap; i++) {
                articles.add(filtered.get(i));
            }
        } else {
            articles = ingestionPort.fetchArticles(topic, limit);
        }
        log.debug("listArticles() | fetched {} articles from ingestionPort", articles.size());

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
