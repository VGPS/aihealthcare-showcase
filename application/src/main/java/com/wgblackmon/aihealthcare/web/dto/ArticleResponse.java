package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;

/**
 * Response DTO representing a single persisted news article.
 *
 * <p>Returned by {@code GET /api/v1/articles?topic={topic}&limit={limit}}.
 * The {@code url} field is serialized as a plain string (the underlying domain
 * type is {@code java.net.URI}, converted in the controller).
 *
 * @param articleId    Stable article identifier.
 * @param title        Article headline.
 * @param url          Canonical article URL as a string.
 * @param topic        Feed source name or search keyword used to categorize this article.
 * @param author       Byline; {@code null} if not determinable.
 * @param sourceName   Human-readable feed label (e.g. "PubMed AI Healthcare").
 * @param sourceTier   Harvest tier: "ACADEMIC", "REGULATORY", or "INDUSTRY".
 * @param sourceWeight Baseline relevance multiplier [0.0, 1.0].
 * @param publishedAt  Publication timestamp; {@code null} if not determinable.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
public record ArticleResponse(
        String articleId,
        String title,
        String url,
        String topic,
        String author,
        String sourceName,
        String sourceTier,
        double sourceWeight,
        Instant publishedAt
) {}
