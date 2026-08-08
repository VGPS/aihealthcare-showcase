package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for generating analyst-grade trend summaries.
 *
 * <p>Implementations produce multi-paragraph research summaries for rising
 * trend keywords, intended for analysts and investors. The port accepts a
 * keyword and its driving articles, returning a prose summary or {@code null}
 * if the service is unavailable.
 *
 * <p>This port is separate from {@link ArticleScoringPort} because trend
 * summarization is a deep-research task (potentially using external web
 * search), not a simple article-scoring evaluation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-28
 * @updated 2026-07-28
 */
public interface TrendSummaryPort {

    /**
     * Generates a research summary for the given trend keyword.
     *
     * @param keyword  the rising trend keyword (e.g. "radiology ai")
     * @param articles articles driving this trend, for context
     * @return a prose summary suitable for an executive brief, or {@code null}
     *         if the service is unavailable or the call fails
     */
    String generateSummary(String keyword, List<NewsArticle> articles);

    /**
     * Returns {@code true} if the adapter is configured and ready to produce
     * summaries (e.g. API key present).
     */
    boolean isAvailable();
}
