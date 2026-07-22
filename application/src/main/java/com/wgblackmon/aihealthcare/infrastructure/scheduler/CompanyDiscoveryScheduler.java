package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyDiscoveryResult;
import com.wgblackmon.aihealthcare.domain.model.CompanyEvent;
import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.DiscoverCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import com.wgblackmon.aihealthcare.domain.service.CompanyProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled driver that runs the company discovery pipeline daily.
 *
 * <p>Scrapes YC, TopStartups.io, and anchor company directories, classifies
 * and deduplicates results, persists new companies as {@code NewsArticle}
 * records under the "New AI Healthcare Companies" topic, and creates/updates
 * {@link CompanyProfile} records with event timelines.
 *
 * <p>Default schedule: daily at 04:00 UTC (same time as RSS harvest +
 * wiki compilation). Configurable via
 * {@code aihealthcare.harvest.company-discovery-cron} in {@code application.yml}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-19
 * @updated 2026-07-22
 */
@Slf4j
@Component
public class CompanyDiscoveryScheduler {

    private final DiscoverCompaniesUseCase discoverCompaniesUseCase;
    private final CompanyProfilePort companyProfilePort;
    private final CompanyEventPort companyEventPort;
    private final CompanyProfileService companyProfileService;

    public CompanyDiscoveryScheduler(DiscoverCompaniesUseCase discoverCompaniesUseCase,
                                     CompanyProfilePort companyProfilePort,
                                     CompanyEventPort companyEventPort,
                                     CompanyProfileService companyProfileService) {
        log.debug("CompanyDiscoveryScheduler() | discoverCompaniesUseCase={}, companyProfilePort={}, companyEventPort={}, companyProfileService={}",
                discoverCompaniesUseCase.getClass().getSimpleName(),
                companyProfilePort.getClass().getSimpleName(),
                companyEventPort.getClass().getSimpleName(),
                companyProfileService.getClass().getSimpleName());
        this.discoverCompaniesUseCase = discoverCompaniesUseCase;
        this.companyProfilePort = companyProfilePort;
        this.companyEventPort = companyEventPort;
        this.companyProfileService = companyProfileService;
    }

    /**
     * Daily company discovery + profile creation.
     * Cron configured via {@code aihealthcare.harvest.company-discovery-cron}
     * (default: daily 04:00 UTC, same as RSS harvest).
     */
    @Scheduled(cron = "${aihealthcare.harvest.company-discovery-cron}", zone = "UTC")
    public void runDailyCompanyDiscovery() {
        log.debug("runDailyCompanyDiscovery() | starting daily company discovery");
        try {
            CompanyDiscoveryResult result = discoverCompaniesUseCase.discover();
            log.info("runDailyCompanyDiscovery() | discovery complete — scraped={}, afterDedup={}, aiHealthFiltered={}",
                    result.totalScraped(), result.afterDedup(), result.aiHealthFiltered());

            int created = 0;
            int updated = 0;

            for (Company company : result.companies()) {
                String slug = companyProfileService.toSlug(company.name());
                Optional<CompanyProfile> existing = companyProfilePort.findBySlug(slug);

                // Build a synthetic article from the company's discovery data
                List<NewsArticle> relatedArticles = new ArrayList<>();
                String articleId = "company-" + slug;
                NewsArticle syntheticArticle = new NewsArticle(
                        articleId, company.name(),
                        URI.create(company.url() != null ? company.url() : "https://example.com"),
                        company.description(), "New AI Healthcare Companies",
                        null, null, company.source(), "INDUSTRY", 0.5, Instant.now());
                relatedArticles.add(syntheticArticle);

                CompanyProfile profile = companyProfileService.upsertFromDiscovery(
                        company, relatedArticles, existing.orElse(null));
                companyProfilePort.save(profile);

                // Detect events from the synthetic article
                List<CompanyEvent> events = companyProfileService.detectEvents(profile, relatedArticles);
                for (CompanyEvent event : events) {
                    companyEventPort.save(event);
                }

                if (existing.isPresent()) {
                    updated++;
                } else {
                    created++;
                }
            }

            log.info("runDailyCompanyDiscovery() | profiles created={}, updated={}, total={}",
                    created, updated, result.companies().size());
        } catch (Exception e) {
            log.warn("runDailyCompanyDiscovery() | company discovery failed — scheduler continues", e);
        }
        log.debug("runDailyCompanyDiscovery() | return=void");
    }
}
