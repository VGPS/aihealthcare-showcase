package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.KnowledgeCompilationPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineAsyncRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST controller for manually triggering wiki compilation.
 *
 * <p>Provides a manual trigger at {@code POST /monitoring/wiki/compile} that:
 * <ol>
 *   <li>Fetches articles from the last 7 days</li>
 *   <li>Calls the {@link KnowledgeCompilationPort} to compile knowledge</li>
 *   <li>Returns the compilation report as JSON</li>
 * </ol>
 *
 * <p>This endpoint is intended for debugging and manual testing.
 * Automated compilation is handled by the {@code FeedHarvestScheduler}.
 * The path {@code /monitoring/**} is already {@code permitAll()} in
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-09-08
 */
@Slf4j
@RestController
public class WikiCompilationController {

    private final KnowledgeCompilationPort compilationPort;
    private final NewsArticleRepository articleRepository;
    private final PipelineAsyncRunner asyncRunner;

    public WikiCompilationController(KnowledgeCompilationPort compilationPort,
                                      NewsArticleRepository articleRepository,
                                      PipelineAsyncRunner asyncRunner) {
        log.debug("WikiCompilationController() | compilationPort={}, articleRepository={}, asyncRunner={}",
                compilationPort.getClass().getSimpleName(),
                articleRepository.getClass().getSimpleName(),
                asyncRunner.getClass().getSimpleName());
        this.compilationPort = compilationPort;
        this.articleRepository = articleRepository;
        this.asyncRunner = asyncRunner;
    }

    /**
     * Triggers a manual wiki compilation using articles from the last 1 day.
     *
     * @return compilation report with created/updated pages and contradiction count
     */
    @PostMapping("/monitoring/wiki/compile")
    public ResponseEntity<Map<String, Object>> triggerCompilation() {
        log.debug("triggerCompilation() | (no args)");

        return asyncRunner.runAsync("wiki-compile", () -> {
            Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
            List<NewsArticleEntity> entities = articleRepository.findByCreatedAtAfterOrderByCreatedAtAsc(since);
            List<NewsArticle> articles = new ArrayList<>();
            for (NewsArticleEntity entity : entities) {
                articles.add(mapToDomain(entity));
            }
            log.info("triggerCompilation() | found {} articles from last 1 day", articles.size());
            compilationPort.compileNewSources(articles);
        });
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
