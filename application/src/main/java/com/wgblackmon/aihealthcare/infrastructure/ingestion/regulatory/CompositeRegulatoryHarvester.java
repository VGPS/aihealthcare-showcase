package com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryHarvestingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates results from all {@link RegulatorySourceHarvester} implementations
 * into a single {@link RegulatoryHarvestingPort}.
 *
 * <p>Failures in individual harvesters are caught and logged; the overall
 * harvest continues. Configuration (lookback days, AI keywords) comes from
 * {@link RegulatoryHarvestProperties}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
@Component
public class CompositeRegulatoryHarvester implements RegulatoryHarvestingPort {

    private final List<RegulatorySourceHarvester> harvesters;
    private final RegulatoryHarvestProperties properties;

    public CompositeRegulatoryHarvester(List<RegulatorySourceHarvester> harvesters,
                                        RegulatoryHarvestProperties properties) {
        log.debug("CompositeRegulatoryHarvester() | harvesters={}, properties={}",
                  harvesters.size(), properties.getClass().getSimpleName());
        this.harvesters = harvesters;
        this.properties = properties;
    }

    @Override
    public List<RegulatoryEvent> harvestAll() {
        log.debug("harvestAll()");

        if (!properties.isEnabled()) {
            log.info("harvestAll() | regulatory harvesting is disabled");
            log.debug("harvestAll() | return=0 events");
            return List.of();
        }

        List<RegulatoryEvent> allEvents = new ArrayList<>();
        int lookbackDays = properties.getLookbackDays();
        List<String> aiKeywords = properties.getAiKeywords();

        for (RegulatorySourceHarvester harvester : harvesters) {
            try {
                List<RegulatoryEvent> events = harvester.harvest(lookbackDays, aiKeywords);
                log.info("harvestAll() | {} returned {} events", harvester.sourceName(), events.size());
                allEvents.addAll(events);
            } catch (Exception e) {
                log.warn("harvestAll() | {} failed: {}", harvester.sourceName(), e.getMessage());
            }
        }

        log.info("harvestAll() | total {} events from {} sources",
                 allEvents.size(), harvesters.size());
        log.debug("harvestAll() | return={} events", allEvents.size());
        return allEvents;
    }
}
