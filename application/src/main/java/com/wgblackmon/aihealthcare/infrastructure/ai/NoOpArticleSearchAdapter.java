package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Fallback {@link ArticleSearchPort} used when the pgvector {@code VectorStore} bean is
 * not present (e.g. when running with the {@code h2} profile).
 *
 * <p>Always returns an empty list — RAG features are silently disabled, but the
 * application context starts successfully without a live pgvector instance.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
@Slf4j
@Component
@ConditionalOnMissingBean(ArticleSearchPort.class)
public class NoOpArticleSearchAdapter implements ArticleSearchPort {

    /**
     * Returns an empty list — vector search is unavailable without pgvector.
     *
     * @param query  search query (ignored)
     * @param topK   max results (ignored)
     * @return empty list
     */
    @Override
    public List<NewsArticle> findSimilar(String query, int topK) {
        log.debug("findSimilar() | query={}, topK={} (no-op — pgvector not configured)", query, topK);
        List<NewsArticle> result = Collections.emptyList();
        log.debug("findSimilar() | return={}", result);
        return result;
    }
}
