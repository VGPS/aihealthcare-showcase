package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.model.ModelInfo;

import java.util.List;

/**
 * Inbound port — drive an AI-enhanced search that retrieves articles via
 * vector similarity, then synthesizes them through multiple LLM models
 * for side-by-side comparison.
 *
 * <p>Implementations live in {@code domain.service}. Controllers and
 * schedulers depend on this interface only — never on the concrete service.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-06-02
 * @updated 2026-07-10
 */
public interface ConductAiSearchUseCase {

    /**
     * Executes an AI-enhanced search using all configured models.
     *
     * @param query the natural-language search query; must not be blank
     * @param topK  maximum number of articles to retrieve from the vector store
     * @return search result containing articles and model syntheses; never null
     */
    AiSearchResult search(String query, int topK);

    /**
     * Executes an AI-enhanced search using only the specified models.
     *
     * @param query      the natural-language search query; must not be blank
     * @param topK       maximum number of articles to retrieve from the vector store
     * @param modelNames model names to include (e.g. "Claude", "GPT", "Perplexity");
     *                   if null or empty, all models are used
     * @return search result containing articles and model syntheses; never null
     */
    AiSearchResult search(String query, int topK, List<String> modelNames);

    /**
     * Returns the list of currently registered AI model providers and their
     * configured model identifiers.
     *
     * @return list of available models; never null, may be empty
     */
    List<ModelInfo> availableModels();
}
