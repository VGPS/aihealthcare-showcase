package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.CompanyDiscoveryResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.DiscoverCompaniesUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled driver that runs the company discovery pipeline weekly.
 *
 * <p>Scrapes YC, TopStartups.io, and anchor company directories, classifies
 * and deduplicates results, and persists new companies as {@code NewsArticle}
 * records under the "New AI Healthcare Companies" topic.
 *
 * <p>These directory sources update infrequently (new startups appear
 * weekly/monthly), so a weekly cadence avoids unnecessary scraping while
 * keeping the company list reasonably current.
 *
 * <p>Default schedule: Saturdays at 06:30 UTC (after competitor harvest,
 * before industry feeds). Configurable via
 * {@code aihealthcare.harvest.company-discovery-cron} in {@code application.yml}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-19
 * @updated 2026-07-19
 */
@Slf4j
@Component
public class CompanyDiscoveryScheduler {

    private final DiscoverCompaniesUseCase discoverCompaniesUseCase;

    public CompanyDiscoveryScheduler(DiscoverCompaniesUseCase discoverCompaniesUseCase) {
        log.debug("CompanyDiscoveryScheduler() | discoverCompaniesUseCase={}",
                discoverCompaniesUseCase.getClass().getSimpleName());
        this.discoverCompaniesUseCase = discoverCompaniesUseCase;
    }

    /**
     * Weekly company discovery harvest.
     * Cron configured via {@code aihealthcare.harvest.company-discovery-cron}
     * (default: Saturdays 06:30 UTC).
     */
    @Scheduled(cron = "${aihealthcare.harvest.company-discovery-cron}", zone = "UTC")
    public void runWeeklyCompanyDiscovery() {
        log.debug("runWeeklyCompanyDiscovery() | starting weekly company discovery");
        try {
            CompanyDiscoveryResult result = discoverCompaniesUseCase.discover();
            log.info("runWeeklyCompanyDiscovery() | complete — scraped={}, afterDedup={}, aiHealthFiltered={}",
                    result.totalScraped(), result.afterDedup(), result.aiHealthFiltered());
        } catch (Exception e) {
            log.warn("runWeeklyCompanyDiscovery() | company discovery failed — scheduler continues", e);
        }
        log.debug("runWeeklyCompanyDiscovery() | return=void");
    }
}
