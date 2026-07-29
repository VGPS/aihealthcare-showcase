package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.ExtractedTrend;

import java.util.List;

/**
 * Outbound port for LLM-based trend topic extraction.
 *
 * <p>Implementations send a numbered list of article titles to an LLM and
 * receive back semantically meaningful trend topics with linked article indices.
 * This replaces the n-gram frequency approach with intelligent theme identification.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-29
 * @updated 2026-07-29
 */
public interface TrendTopicExtractionPort {

    /**
     * Extracts emerging trend topics from a batch of article titles.
     *
     * @param articleTitles numbered list of article titles (recent articles)
     * @param maxTopics    maximum number of trend topics to return
     * @return extracted trends with labels, descriptions, and linked article indices
     */
    List<ExtractedTrend> extractTopics(List<String> articleTitles, int maxTopics);
}
