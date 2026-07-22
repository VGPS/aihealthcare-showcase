package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryHarvestingPort;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Application-layer service implementing the regulatory event monitoring use case.
 *
 * <p>Orchestrates harvest, deduplication, and persistence of regulatory events.
 * Retrieval methods delegate directly to {@link RegulatoryEventPort}.
 *
 * <p>This class carries no Spring annotations — it is wired as a {@code @Bean}
 * in {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
public class RegulatoryEventService implements MonitorRegulatoryEventsUseCase {

    private final RegulatoryEventPort regulatoryEventPort;
    private final RegulatoryHarvestingPort regulatoryHarvestingPort;

    public RegulatoryEventService(RegulatoryEventPort regulatoryEventPort,
                                  RegulatoryHarvestingPort regulatoryHarvestingPort) {
        log.debug("RegulatoryEventService() | regulatoryEventPort={}, regulatoryHarvestingPort={}",
                  regulatoryEventPort, regulatoryHarvestingPort);
        this.regulatoryEventPort = regulatoryEventPort;
        this.regulatoryHarvestingPort = regulatoryHarvestingPort;
    }

    @Override
    public List<RegulatoryEvent> getRecentEvents(int limit) {
        log.debug("getRecentEvents() | limit={}", limit);

        List<RegulatoryEvent> result = regulatoryEventPort.findRecent(limit);

        log.debug("getRecentEvents() | return={} events", result.size());
        return result;
    }

    @Override
    public List<RegulatoryEvent> getEventsByType(RegulatoryEventType type, int limit) {
        log.debug("getEventsByType() | type={}, limit={}", type, limit);

        List<RegulatoryEvent> result = regulatoryEventPort.findByType(type, limit);

        log.debug("getEventsByType() | return={} events", result.size());
        return result;
    }

    @Override
    public List<RegulatoryEvent> getEventsByBody(RegulatoryBody body, int limit) {
        log.debug("getEventsByBody() | body={}, limit={}", body, limit);

        List<RegulatoryEvent> result = regulatoryEventPort.findByBody(body, limit);

        log.debug("getEventsByBody() | return={} events", result.size());
        return result;
    }

    @Override
    public Optional<RegulatoryEvent> getEvent(String eventId) {
        log.debug("getEvent() | eventId={}", eventId);

        Optional<RegulatoryEvent> result = regulatoryEventPort.findById(eventId);

        log.debug("getEvent() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public int triggerHarvest() {
        log.debug("triggerHarvest()");

        List<RegulatoryEvent> harvested = regulatoryHarvestingPort.harvestAll();
        log.info("triggerHarvest() | harvested {} raw events", harvested.size());

        List<RegulatoryEvent> newEvents = new ArrayList<>();
        for (RegulatoryEvent event : harvested) {
            boolean duplicate = false;
            if (event.referenceNumber() != null && !event.referenceNumber().isBlank()) {
                duplicate = regulatoryEventPort.existsByReferenceNumber(event.referenceNumber());
            }
            if (!duplicate) {
                duplicate = regulatoryEventPort.existsBySourceUrl(event.sourceUrl());
            }
            if (!duplicate) {
                newEvents.add(event);
            }
        }

        if (!newEvents.isEmpty()) {
            regulatoryEventPort.saveAll(newEvents);
        }

        log.info("triggerHarvest() | saved {} new events (filtered {} duplicates)",
                 newEvents.size(), harvested.size() - newEvents.size());
        log.debug("triggerHarvest() | return={}", newEvents.size());
        return newEvents.size();
    }
}
