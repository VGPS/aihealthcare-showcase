package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.scheduler.RegulatoryHarvestScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller providing a manual trigger for the regulatory harvest pipeline.
 *
 * <p>Delegates to {@link RegulatoryHarvestScheduler#runDailyRegulatoryHarvest()}
 * so the full harvest → dedup → save → watchlist-match cycle runs on demand.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
@RestController
public class RegulatoryMonitoringController {

    private final RegulatoryHarvestScheduler regulatoryHarvestScheduler;

    public RegulatoryMonitoringController(RegulatoryHarvestScheduler regulatoryHarvestScheduler) {
        log.debug("RegulatoryMonitoringController() | regulatoryHarvestScheduler={}",
                  regulatoryHarvestScheduler);
        this.regulatoryHarvestScheduler = regulatoryHarvestScheduler;
    }

    /**
     * Manually triggers a regulatory harvest run.
     *
     * @return JSON map with status message
     */
    @PostMapping("/monitoring/regulatory/harvest")
    public ResponseEntity<Map<String, String>> triggerRegulatoryHarvest() {
        log.debug("triggerRegulatoryHarvest() | (no args)");

        regulatoryHarvestScheduler.runDailyRegulatoryHarvest();

        Map<String, String> response = Map.of("status", "Regulatory harvest completed");
        log.debug("triggerRegulatoryHarvest() | return={}", response);
        return ResponseEntity.ok(response);
    }
}
