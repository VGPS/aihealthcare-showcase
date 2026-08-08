package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for generating an executive summary of a batch of articles
 * for the daily digest email.
 *
 * <p>Implementations call an LLM to produce a concise 3-5 paragraph summary
 * with {@code [N]} citation markers referencing numbered articles. The summary
 * targets healthcare executives and investors.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-05
 * @updated 2026-08-05
 */
public interface DigestSummaryPort {

    /**
     * Generates an executive summary of the given articles with citation markers.
     *
     * @param articles the articles to summarize; must not be empty
     * @return HTML-safe summary text with {@code [N]} citation markers
     */
    String generateDigestSummary(List<NewsArticle> articles);
}
