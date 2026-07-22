package com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;

import java.util.List;

/**
 * Internal interface for individual regulatory source harvesters.
 *
 * <p>Each implementation scrapes or queries a single regulatory data source
 * (e.g. FDA 510(k), CMS Federal Register). The
 * {@link CompositeRegulatoryHarvester} aggregates results from all
 * implementations.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface RegulatorySourceHarvester {

    /**
     * Returns a short label for this source (used in logging).
     */
    String sourceName();

    /**
     * Harvests regulatory events from this source.
     *
     * @param lookbackDays how many days back to check
     * @param aiKeywords   keywords used to filter for AI-relevance
     * @return list of discovered events
     */
    List<RegulatoryEvent> harvest(int lookbackDays, List<String> aiKeywords);
}
