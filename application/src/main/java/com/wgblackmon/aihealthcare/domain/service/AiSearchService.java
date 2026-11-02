package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.ModelInfo;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AdminNotificationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain service implementing AI-enhanced search with multi-model LLM synthesis.
 *
 * <p>Retrieves semantically similar articles from the vector store via
 * {@link ArticleSearchPort}, then synthesizes via one or more registered
 * {@link AiSearchPort} adapters (e.g. Claude, GPT, Perplexity, Gemini).
 *
 * <p>Cost discipline (see {@code CLAUDE.md} "Production LLM Cost
 * Discipline"): when the caller doesn't specify {@code modelNames}, the
 * search defaults to the single {@value #DEFAULT_MODEL_NAME} model rather
 * than fanning out to every available provider — multi-model comparison is
 * opt-in via an explicit {@code modelNames} list. Falls back to all
 * available models only if the default model is unavailable.
 *
 * <p>This class has no Spring annotations — it is wired as a bean in
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.3
 * @since   2026-06-02
 * @updated 2026-11-02
 */
public class AiSearchService implements ConductAiSearchUseCase {

    private static final DomainLogger log = new DomainLogger(AiSearchService.class);

    private static final String DEFAULT_MODEL_NAME = "Claude";

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
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Instant.now());
            log.debug("search() | return={}", result);
            return result;
        }

        List<NewsArticle> articles = vectorSearch.findSimilar(query.trim(), topK);
        log.info("search() | vector search returned {} articles for query '{}'", articles.size(), query);

        if (articles.isEmpty()) {
            AiSearchResult result = new AiSearchResult(
                    UUID.randomUUID().toString(), query,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Instant.now());
            log.debug("search() | return=AiSearchResult[articles=0, syntheses=0]");
            return result;
        }

        // Filter ports by requested model names (null/empty = default to Claude-only;
        // fall back to all available models only if Claude itself is unavailable).
        List<AiSearchPort> selectedPorts = new ArrayList<>();
        if (modelNames == null || modelNames.isEmpty()) {
            for (AiSearchPort port : aiSearchPorts) {
                if (port.isAvailable() && DEFAULT_MODEL_NAME.equalsIgnoreCase(port.modelName())) {
                    selectedPorts.add(port);
                }
            }
            if (selectedPorts.isEmpty()) {
                log.warn("search() | default model '{}' unavailable — falling back to all available models",
                         DEFAULT_MODEL_NAME);
                for (AiSearchPort port : aiSearchPorts) {
                    if (port.isAvailable()) {
                        selectedPorts.add(port);
                    }
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
        List<String> noMatchModelNames = new ArrayList<>();
        for (AiSearchPort port : selectedPorts) {
            try {
                log.info("search() | synthesizing with model '{}'", port.modelName());
                AiSearchSynthesis synthesis = port.synthesize(query, articles);
                if (synthesis != null) {
                    syntheses.add(synthesis);
                    log.info("search() | synthesis complete from '{}': {} key findings",
                             port.modelName(), synthesis.keyFindings().size());
                } else {
                    noMatchModelNames.add(port.modelName());
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
                articles, syntheses, noMatchModelNames, Instant.now());
        log.debug("search() | return=AiSearchResult[articles={}, syntheses={}, noMatch={}]",
                  articles.size(), syntheses.size(), noMatchModelNames.size());
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
