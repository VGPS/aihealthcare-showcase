package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.NewsletterDraft;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;

/**
 * Inbound port — drive newsletter generation and draft retrieval.
 *
 * <p>The implementation lives in {@code application} and orchestrates calls to
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort}.
 * Accepts the {@code runId} from a completed ingestion run and returns a fully
 * assembled {@link NewsletterDraft}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2025-01-27
 * @updated 2026-04-04
 */
public interface GenerateNewsletterUseCase {

    /**
     * Generate a newsletter draft from articles previously ingested under {@code runId}.
     *
     * @param runId               The ingestion run ID whose articles should be summarized.
     * @param draftId             Unique identifier to assign to the resulting draft.
     * @param newsletterTitle     Title for this newsletter issue.
     * @param tone                Desired writing tone for AI-generated content.
     * @param maxSectionsPerTopic Maximum number of sections to generate per topic.
     * @return A fully assembled {@link NewsletterDraft} including sources for attribution.
     * @throws com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException if no ingestion
     *         run exists for the given {@code runId}.
     * @throws com.wgblackmon.aihealthcare.domain.exception.NoArticlesFoundException if the run
     *         exists but produced no processable articles.
     */
    NewsletterDraft generate(
            String runId,
            String draftId,
            String newsletterTitle,
            NewsletterTone tone,
            int maxSectionsPerTopic
    );

    /**
     * Retrieve a previously generated draft by its identifier.
     *
     * @param draftId The draft identifier assigned during {@link #generate}.
     * @return The matching {@link NewsletterDraft}.
     * @throws com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException if no
     *         draft exists for the given {@code draftId}.
     */
    NewsletterDraft getDraft(String draftId);
}
