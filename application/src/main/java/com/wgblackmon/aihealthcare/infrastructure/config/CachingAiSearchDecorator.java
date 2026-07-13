package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.model.ModelInfo;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase;
import com.wgblackmon.aihealthcare.domain.service.AiSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;

/**
 * Caching decorator for {@link AiSearchService} that wraps the domain service
 * with a Caffeine TTL-based cache to reduce AI API costs on repeated queries.
 *
 * <p>Cache key is derived from (query, topK, modelNames). Entries expire after
 * the TTL configured in {@code application.yml} under
 * {@code spring.cache.caffeine.spec}.
 *
 * <p>This decorator exists in infrastructure because {@link AiSearchService}
 * lives in the domain layer and cannot carry Spring annotations.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-07-03
 * @updated 2026-07-10
 */
@Slf4j
public class CachingAiSearchDecorator implements ConductAiSearchUseCase {

    private final AiSearchService delegate;

    public CachingAiSearchDecorator(AiSearchService delegate) {
        log.debug("CachingAiSearchDecorator() | delegate={}", delegate.getClass().getSimpleName());
        this.delegate = delegate;
    }

    @Override
    @Cacheable(value = "ai-search", key = "#query + ':' + #topK")
    public AiSearchResult search(String query, int topK) {
        log.debug("search() | CACHE MISS query={}, topK={}", query, topK);
        AiSearchResult result = delegate.search(query, topK);
        log.debug("search() | return=AiSearchResult[articles={}, syntheses={}]",
                  result.articles().size(), result.syntheses().size());
        return result;
    }

    @Override
    @Cacheable(value = "ai-search", key = "#query + ':' + #topK + ':' + (#modelNames != null ? #modelNames.toString() : 'all')")
    public AiSearchResult search(String query, int topK, List<String> modelNames) {
        log.debug("search() | CACHE MISS query={}, topK={}, modelNames={}", query, topK, modelNames);
        AiSearchResult result = delegate.search(query, topK, modelNames);
        log.debug("search() | return=AiSearchResult[articles={}, syntheses={}]",
                  result.articles().size(), result.syntheses().size());
        return result;
    }

    @Override
    public List<ModelInfo> availableModels() {
        log.debug("availableModels() | delegating to AiSearchService");
        List<ModelInfo> result = delegate.availableModels();
        log.debug("availableModels() | return={}", result);
        return result;
    }
}
