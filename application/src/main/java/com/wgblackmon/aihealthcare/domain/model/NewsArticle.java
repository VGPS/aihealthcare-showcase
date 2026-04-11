package com.wgblackmon.aihealthcare.domain.model;

import java.net.URI;
import java.time.Instant;

/**
 * Immutable domain record representing a single harvested or ingested news article.
 *
 * <p>Carries both content fields (title, URL, body text, topic, author) and
 * feed-source metadata fields (sourceName, sourceTier, sourceWeight, topicId)
 * that the AI relevance-scoring layer uses as priors before applying semantic
 * similarity scoring.  This record is the primary input to
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort}.
 *
 * <p>{@code topicId} scopes this article to a specific
 * {@link com.wgblackmon.aihealthcare.domain.model.Topic}, enabling the
 * multi-topic engine to isolate harvested content, vector embeddings, and
 * newsletter output per topic.  It is {@code null} in Slice 1; Slice 2 will
 * populate it from the feed configuration.
 *
 * <p>{@code sourceWeight} is a normalized value in [0.0, 1.0] representing
 * the base trustworthiness of the originating feed:
 * <ul>
 *   <li>0.9 – Academic / peer-reviewed (PubMed, arXiv, npj Digital Medicine)</li>
 *   <li>0.8 – Regulatory (FDA, NLM, ClinicalTrials.gov)</li>
 *   <li>0.5 – Industry / trade news (Becker's, STAT, MedTech Intelligence)</li>
 * </ul>
 *
 * @param articleId     Stable identifier assigned at ingestion time (entry URI, link URL,
 *                      or a generated UUID as fallback).
 * @param title         Title of the article as harvested from the source.
 * @param url           Canonical URL of the source article; used in newsletter attribution.
 * @param bodyText      Extracted body text or RSS description; may be blank for some feeds.
 * @param topic         Feed-source name or search keyword used to categorize this article
 *                      (used for grouping sections in newsletter generation).
 * @param author        Byline of the article's author; {@code null} if not determinable.
 * @param topicId       FK to {@link Topic#id()}; {@code null} until Slice 2 adds persistence.
 * @param sourceName    Human-readable feed or source label (e.g., "PubMed AI Healthcare").
 * @param sourceTier    Harvest tier label (e.g., "ACADEMIC", "REGULATORY", "INDUSTRY");
 *                      {@code null} for manually ingested articles.
 * @param sourceWeight  Baseline relevance multiplier [0.0, 1.0]; {@code 0.5} for unknown sources.
 * @param publishedAt   Publication or harvest timestamp; {@code null} if not determinable.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2025-01-27
 * @updated 2026-04-10
 */
public record NewsArticle(
        String articleId,
        String title,
        URI url,
        String bodyText,
        String topic,
        String author,
        Long topicId,
        String sourceName,
        String sourceTier,
        double sourceWeight,
        Instant publishedAt
) {
    /**
     * Compact canonical constructor — validates required fields.
     */
    public NewsArticle {
        if (articleId == null || articleId.isBlank()) {
            throw new IllegalArgumentException("articleId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (url == null) {
            throw new IllegalArgumentException("url must not be null");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
    }
}
