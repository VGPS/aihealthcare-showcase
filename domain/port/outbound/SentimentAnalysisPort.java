package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.ArticleSentiment;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port — classify the sentiment of news articles using an LLM.
 *
 * <p>Implementations live in {@code infrastructure/ai} and send a batch of
 * articles to an LLM with a sentiment classification prompt. Each article
 * receives a POSITIVE/NEGATIVE/MIXED/NEUTRAL label, a confidence score,
 * and a one-sentence rationale.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public interface SentimentAnalysisPort {

    /**
     * Classify the sentiment of a batch of articles toward a given company.
     *
     * @param articles    the articles to classify; must not be empty
     * @param companyName the company to evaluate sentiment toward
     * @return per-article sentiment results; same order as input where possible
     */
    List<ArticleSentiment> analyzeSentiment(List<NewsArticle> articles, String companyName);
}
