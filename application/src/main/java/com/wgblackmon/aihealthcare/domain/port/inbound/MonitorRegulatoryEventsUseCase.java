package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for the regulatory event monitoring use case.
 *
 * <p>Provides operations to retrieve regulatory events for display
 * and to trigger an on-demand harvest cycle. The implementation
 * delegates to {@link com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort}
 * for persistence and
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryHarvestingPort}
 * for discovery.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface MonitorRegulatoryEventsUseCase {

    /**
     * Returns the most recent regulatory events.
     *
     * @param limit maximum number of events to return
     * @return events ordered by discoveredAt descending
     */
    List<RegulatoryEvent> getRecentEvents(int limit);

    /**
     * Returns recent events filtered by event type.
     */
    List<RegulatoryEvent> getEventsByType(RegulatoryEventType type, int limit);

    /**
     * Returns recent events filtered by regulatory body.
     */
    List<RegulatoryEvent> getEventsByBody(RegulatoryBody body, int limit);

    /**
     * Returns a single event by ID.
     */
    Optional<RegulatoryEvent> getEvent(String eventId);

    /**
     * Triggers an on-demand harvest of regulatory events from all sources.
     *
     * @return the number of new events discovered and stored
     */
    int triggerHarvest();
}
