package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineAsyncRunner;
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
 * @updated 2026-09-08
 */
@Slf4j
@RestController
public class RegulatoryMonitoringController {

    private final RegulatoryHarvestScheduler regulatoryHarvestScheduler;
    private final PipelineAsyncRunner asyncRunner;

    public RegulatoryMonitoringController(RegulatoryHarvestScheduler regulatoryHarvestScheduler,
                                           PipelineAsyncRunner asyncRunner) {
        log.debug("RegulatoryMonitoringController() | regulatoryHarvestScheduler={}, asyncRunner={}",
                  regulatoryHarvestScheduler, asyncRunner);
        this.regulatoryHarvestScheduler = regulatoryHarvestScheduler;
        this.asyncRunner = asyncRunner;
    }

    /**
     * Manually triggers a regulatory harvest run.
     *
     * @return JSON map with status message
     */
    @PostMapping("/monitoring/regulatory/harvest")
    public ResponseEntity<Map<String, Object>> triggerRegulatoryHarvest() {
        log.debug("triggerRegulatoryHarvest() | (no args)");

        return asyncRunner.runAsync("regulatory", () -> regulatoryHarvestScheduler.runDailyRegulatoryHarvest());
    }
}
