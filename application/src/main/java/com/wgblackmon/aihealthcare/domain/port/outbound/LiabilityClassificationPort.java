package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.LiabilityIncident;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.Optional;

/**
 * Outbound port for LLM-based classification of news articles into
 * {@link LiabilityIncident} proposals.
 *
 * <p>The implementing adapter calls an LLM to decide whether the article describes
 * a qualifying AI healthcare liability incident. If it does, an incident with
 * {@code status = PENDING_REVIEW} is returned for admin approval. If not, or if
 * confidence is too low, {@code Optional.empty()} is returned.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-15
 * @updated 2026-11-15
 */
public interface LiabilityClassificationPort {

    /**
     * Classify the given article. Returns an incident proposal with
     * {@code status = PENDING_REVIEW} if the article describes a qualifying
     * incident with HIGH or MEDIUM confidence, otherwise empty.
     */
    Optional<LiabilityIncident> classify(NewsArticle article);
}
