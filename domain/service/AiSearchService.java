package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.ModelInfo;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AdminNotificationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain service implementing AI-enhanced search with multi-model LLM synthesis.
 *
 * <p>Retrieves semantically similar articles from the vector store via
 * {@link ArticleSearchPort}, then fans out to each registered
 * {@link AiSearchPort} adapter to produce parallel AI syntheses from
 * different models (e.g. Claude and GPT).
 *
 * <p>This class has no Spring annotations — it is wired as a bean in
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-06-02
 * @updated 2026-07-10
 */
@Slf4j
public class AiSearchService implements ConductAiSearchUseCase {

    private final ArticleSearchPort vectorSearch;
    private final List<AiSearchPort> aiSearchPorts;
    private final AdminNotificationPort adminNotifier;

    public AiSearchService(ArticleSearchPort vectorSearch,
                           List<AiSearchPort> aiSearchPorts,
                           AdminNotificationPort adminNotifier) {
        log.debug("AiSearchService() | vectorSearch={}, aiSearchPortCount={}, adminNotifier={}",
                  vectorSearch.getClass().getSimpleName(), aiSearchPorts.size(),
                  adminNotifier.getClass().getSimpleName());
        this.vectorSearch = vectorSearch;
        this.aiSearchPorts = aiSearchPorts;
        this.adminNotifier = adminNotifier;
        log.debug("AiSearchService() | return=void");
    }

    @Override
    public AiSearchResult search(String query, int topK) {
        log.debug("search() | query={}, topK={}", query, topK);
        AiSearchResult result = search(query, topK, null);
        log.debug("search() | return=AiSearchResult[articles={}, syntheses={}]",
                  result.articles().size(), result.syntheses().size());
        return result;
    }

    @Override
    public AiSearchResult search(String query, int topK, List<String> modelNames) {
        log.debug("search() | query={}, topK={}, modelNames={}", query, topK, modelNames);

        if (query == null || query.isBlank()) {
            log.warn("search() | blank query — returning empty result");
            AiSearchResult result = new AiSearchResult(
                    UUID.randomUUID().toString(), "",
                    Collections.emptyList(), Collections.emptyList(), Instant.now());
            log.debug("search() | return={}", result);
            return result;
        }

        List<NewsArticle> articles = vectorSearch.findSimilar(query.trim(), topK);
        log.info("search() | vector search returned {} articles for query '{}'", articles.size(), query);

        if (articles.isEmpty()) {
            AiSearchResult result = new AiSearchResult(
                    UUID.randomUUID().toString(), query,
                    Collections.emptyList(), Collections.emptyList(), Instant.now());
            log.debug("search() | return=AiSearchResult[articles=0, syntheses=0]");
            return result;
        }

        // Filter ports by requested model names (null/empty = all available models)
        List<AiSearchPort> selectedPorts = new ArrayList<>();
        if (modelNames == null || modelNames.isEmpty()) {
            for (AiSearchPort port : aiSearchPorts) {
                if (port.isAvailable()) {
                    selectedPorts.add(port);
                }
            }
        } else {
            for (AiSearchPort port : aiSearchPorts) {
                if (!port.isAvailable()) {
                    continue;
                }
                for (String requested : modelNames) {
                    if (port.modelName().equalsIgnoreCase(requested)) {
                        selectedPorts.add(port);
                        break;
                    }
                }
            }
            log.info("search() | filtered to {} of {} models: {}",
                     selectedPorts.size(), aiSearchPorts.size(), modelNames);
        }

        List<AiSearchSynthesis> syntheses = new ArrayList<>();
        for (AiSearchPort port : selectedPorts) {
            try {
                log.info("search() | synthesizing with model '{}'", port.modelName());
                AiSearchSynthesis synthesis = port.synthesize(query, articles);
                if (synthesis != null) {
                    syntheses.add(synthesis);
                    log.info("search() | synthesis complete from '{}': {} key findings",
                             port.modelName(), synthesis.keyFindings().size());
                } else {
                    log.info("search() | model '{}' reported NO_MATCH — articles not relevant", port.modelName());
                }
            } catch (Exception e) {
                log.error("search() | synthesis failed for model '{}': {}",
                          port.modelName(), e.getMessage(), e);
                adminNotifier.notifyModelFailure(port.modelName(), port.modelId(), query, e);
                syntheses.add(new AiSearchSynthesis(
                        port.modelName(), "Model not currently available.",
                        Collections.emptyList(), Instant.now()));
            }
        }

        AiSearchResult result = new AiSearchResult(
                UUID.randomUUID().toString(), query,
                articles, syntheses, Instant.now());
        log.debug("search() | return=AiSearchResult[articles={}, syntheses={}]",
                  articles.size(), syntheses.size());
        return result;
    }

    @Override
    public List<ModelInfo> availableModels() {
        log.debug("availableModels() | aiSearchPortCount={}", aiSearchPorts.size());
        List<ModelInfo> result = new ArrayList<>();
        for (AiSearchPort port : aiSearchPorts) {
            if (port.isAvailable()) {
                result.add(new ModelInfo(port.modelName(), port.modelId()));
            }
        }
        log.debug("availableModels() | return={}", result);
        return result;
    }
}
