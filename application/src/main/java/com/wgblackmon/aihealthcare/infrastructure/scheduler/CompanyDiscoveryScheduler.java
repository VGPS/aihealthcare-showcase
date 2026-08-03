package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.service.PerplexityCompanyDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled driver that runs the Perplexity-powered company discovery pipeline
 * weekly.
 *
 * <p>Calls {@link PerplexityCompanyDiscoveryService#runDiscoveryCycle()} which
 * discovers new AI healthcare companies via the Perplexity API, extracts
 * structured fields, cross-validates against curated lists, and persists
 * results to the database.
 *
 * <p>Default schedule: weekly Sunday at 06:00 UTC. Configurable via
 * {@code aihealthcare.company-discovery.schedule} in {@code application.yml}.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-19
 * @updated 2026-08-02
 */
@Slf4j
@Component
public class CompanyDiscoveryScheduler {

    private final PerplexityCompanyDiscoveryService discoveryService;

    public CompanyDiscoveryScheduler(PerplexityCompanyDiscoveryService discoveryService) {
        log.debug("CompanyDiscoveryScheduler() | discoveryService={}",
                discoveryService.getClass().getSimpleName());
        this.discoveryService = discoveryService;
    }

    /**
     * Weekly company discovery via Perplexity API.
     * Cron configured via {@code aihealthcare.company-discovery.schedule}
     * (default: Sunday 06:00 UTC).
     */
    @Scheduled(cron = "${aihealthcare.company-discovery.schedule}", zone = "UTC")
    public void runWeeklyCompanyDiscovery() {
        log.debug("runWeeklyCompanyDiscovery() | starting weekly company discovery");
        try {
            int persisted = discoveryService.runDiscoveryCycle();
            log.info("runWeeklyCompanyDiscovery() | discovery complete — new companies persisted={}",
                    persisted);
        } catch (Exception e) {
            log.warn("runWeeklyCompanyDiscovery() | company discovery failed — scheduler continues", e);
        }
        log.debug("runWeeklyCompanyDiscovery() | return=void");
    }
}
