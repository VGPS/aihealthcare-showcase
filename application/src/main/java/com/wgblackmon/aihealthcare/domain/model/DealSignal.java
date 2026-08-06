package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * A detected business deal signal extracted from a healthcare AI article.
 *
 * <p>Represents a funding round, acquisition, partnership, IPO, or product
 * launch detected via keyword filtering and optionally refined by LLM
 * classification. Linked back to the source article via {@code articleId}.
 *
 * @param signalId        unique identifier (UUID)
 * @param articleId       FK to the source {@link NewsArticle}
 * @param title           article title for display
 * @param signalType      deal classification
 * @param companyName     the primary company involved (extracted by keyword or LLM)
 * @param summary         one-sentence deal summary
 * @param confidence      detection confidence [0.0, 1.0]
 * @param detectedAt      when this signal was detected
 * @param dealAmount      deal value if known (e.g. "$200M", "undisclosed"), nullable
 * @param counterpartyName the other party in the deal, nullable
 * @param sourceUrl       article URL for linking, nullable
 * @param llmAnalysis     LLM-generated deal analysis paragraph, nullable
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-06
 */
public record DealSignal(
        String signalId,
        String articleId,
        String title,
        DealSignalType signalType,
        String companyName,
        String summary,
        double confidence,
        Instant detectedAt,
        String dealAmount,
        String counterpartyName,
        String sourceUrl,
        String llmAnalysis
) {

    public DealSignal {
        if (signalId == null || signalId.isBlank()) {
            throw new IllegalArgumentException("signalId must not be blank");
        }
        if (articleId == null || articleId.isBlank()) {
            throw new IllegalArgumentException("articleId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (signalType == null) {
            throw new IllegalArgumentException("signalType must not be null");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        if (detectedAt == null) {
            throw new IllegalArgumentException("detectedAt must not be null");
        }
    }
}
