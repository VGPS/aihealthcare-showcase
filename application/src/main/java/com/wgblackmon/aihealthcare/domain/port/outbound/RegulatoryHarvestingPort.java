package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;

import java.util.List;

/**
 * Outbound port for harvesting regulatory events from external sources.
 *
 * <p>Implementations scrape or query FDA databases (510(k), De Novo),
 * CMS rules (Federal Register), and other regulatory data sources.
 * Each call returns newly discovered events; deduplication is handled
 * by the caller via {@link RegulatoryEventPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface RegulatoryHarvestingPort {

    /**
     * Harvests regulatory events from all configured sources.
     *
     * @return list of discovered regulatory events (may contain duplicates
     *         relative to previously stored events)
     */
    List<RegulatoryEvent> harvestAll();
}
