package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.LiabilityIncident;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for persisting and querying {@link LiabilityIncident} records.
 *
 * <p>All methods use domain types only — no Spring, JPA, or infrastructure
 * types appear on this interface. The {@link #findRelatedArticles} method
 * returns domain {@link NewsArticle} records matched by the incident's
 * {@code defendant} name against existing harvested article titles, so
 * Phase 2 article linking requires no additional API calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-16
 * @updated 2026-11-16
 */
public interface LiabilityIncidentPort {

    /** Persist a new or updated incident record. */
    void save(LiabilityIncident incident);

    /** Return all incidents ordered by filing date descending, nulls last. */
    List<LiabilityIncident> findAll();

    /** Return the incident with the given id, or {@code null} if not found. */
    LiabilityIncident findById(String incidentId);

    /** Delete the incident with the given id. No-op if not found. */
    void deleteById(String incidentId);

    /**
     * Return up to {@code limit} harvested articles whose title contains the
     * given keyword (case-insensitive). Used to surface related coverage per
     * incident without any LLM call.
     */
    List<NewsArticle> findRelatedArticles(String keyword, int limit);
}
