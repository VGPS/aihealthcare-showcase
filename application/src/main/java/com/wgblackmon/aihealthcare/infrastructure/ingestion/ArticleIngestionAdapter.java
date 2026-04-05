package com.wgblackmon.aihealthcare.infrastructure.ingestion;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub implementation of {@link ArticleIngestionPort} for Slice 1.
 *
 * <p>This adapter satisfies the Spring wiring requirement for Slice 1 without
 * performing real HTTP fetches or RSS parsing. It always returns an empty list,
 * which means any call to {@code NewsletterService.generate()} after ingestion
 * will throw {@code NoArticlesFoundException} until a real implementation is
 * provided in a later slice.
 *
 * <p><b>Why a stub?</b> Hexagonal architecture lets us build and test the full
 * request-response pipeline (controller → service → port) without a working
 * scraper. Replacing this stub with a real HTTP adapter in Slice 2 requires
 * zero changes to the domain or application layers — only this class changes.
 *
 * <p>Slice 2 will replace this stub with a real adapter that fetches from RSS
 * feeds, news APIs, or scraped web pages.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
@Slf4j
@Component
public class ArticleIngestionAdapter implements ArticleIngestionPort {

    /**
     * {@inheritDoc}
     *
     * <p><b>Stub behaviour:</b> always returns an empty list and logs a warning.
     * No network calls are made.
     */
    @Override
    public List<NewsArticle> fetchArticles(String topic, int maxArticles) {
        log.debug("fetchArticles() | topic={}, maxArticles={}", topic, maxArticles);
        log.warn("fetchArticles() | ArticleIngestionAdapter is a stub — returning empty list. "
                 + "Real HTTP ingestion will be implemented in Slice 2.");
        List<NewsArticle> result = new ArrayList<>();
        log.debug("fetchArticles() | return={} articles", result.size());
        return result;
    }
}
