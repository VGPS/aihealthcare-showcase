package com.wgblackmon.aihealthcare.infrastructure.persistence;

import java.time.Instant;

/**
 * JPA interface projection for the deal signals list page.
 *
 * <p>Includes only the fields rendered by {@code deals.html}.
 * Excludes {@code llmAnalysis} (a TEXT column that can hold 1000+ characters of
 * LLM-generated analysis). Spring Data generates a column-specific SELECT that skips that column.
 * {@code articleId} is included because {@link com.wgblackmon.aihealthcare.domain.model.DealSignal}
 * validates it as non-blank.
 *
 * <p>The detail page at {@code /dashboard/deals/{signalId}} still uses
 * {@link DealSignalRepository#findById} which returns the full entity.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-23
 * @updated 2026-08-23
 */
public interface DealSignalListView {

    String getSignalId();

    String getArticleId();

    String getTitle();

    String getSignalType();

    String getCompanyName();

    String getSummary();

    double getConfidence();

    Instant getDetectedAt();

    String getDealAmount();

    String getCounterpartyName();

    String getSourceUrl();
}
