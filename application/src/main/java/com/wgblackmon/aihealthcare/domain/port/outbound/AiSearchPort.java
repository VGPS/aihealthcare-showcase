package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port — synthesize a set of retrieved articles into a concise
 * AI-generated summary with key findings, using a specific LLM model.
 *
 * <p>Multiple implementations of this port may exist simultaneously
 * (one per model — e.g. Anthropic Claude, OpenAI GPT). The domain service
 * iterates over all available implementations to produce multi-model
 * side-by-side comparisons.
 *
 * <p>Implementations live in {@code infrastructure.ai}. Unit tests inject
 * mock implementations so no real AI calls are made outside the
 * {@code ai-integration} Spring profile.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-07-10
 */
public interface AiSearchPort {

    /**
     * Synthesize the given articles into a summary with key findings.
     *
     * @param query    the original search query for context
     * @param articles articles retrieved from the vector store; must not be empty
     * @return an AI-generated synthesis including model name, summary, and findings
     */
    AiSearchSynthesis synthesize(String query, List<NewsArticle> articles);

    /**
     * Returns the human-readable name of the model backing this adapter
     * (e.g. "Claude", "GPT").
     *
     * @return model name; never null or blank
     */
    String modelName();

    /**
     * Returns the specific model identifier configured for this adapter
     * (e.g. "claude-sonnet-4-6", "sonar", "gemini-2.0-flash").
     *
     * @return model ID as configured in application.yml; never null or blank
     */
    String modelId();
}
