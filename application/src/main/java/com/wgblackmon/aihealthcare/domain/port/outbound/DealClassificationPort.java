package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for LLM-powered deal classification and enrichment.
 *
 * <p>Takes keyword-pre-filtered candidate articles and uses an LLM to
 * confirm or reject deal signals, extract structured deal details
 * (amount, counterparty), and generate an analysis paragraph.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
public interface DealClassificationPort {

    /**
     * Classifies a batch of candidate articles into deal signals using LLM.
     * Articles that are not real deals are filtered out. Returned signals
     * include enriched fields (dealAmount, counterpartyName, llmAnalysis).
     *
     * @param candidateArticles articles that passed keyword pre-filtering
     * @return classified deal signals with enriched fields
     */
    List<DealSignal> classifyDeals(List<NewsArticle> candidateArticles);
}
