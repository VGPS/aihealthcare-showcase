package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyDiscoveryResult;
import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.CompanyTags;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.port.inbound.DiscoverCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import com.wgblackmon.aihealthcare.domain.service.CompanyProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CompanyDiscoveryScheduler}.
 *
 * <p>Verifies that the scheduler delegates to the discovery use case,
 * creates company profiles, and survives exceptions without killing the thread.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-19
 * @updated 2026-07-22
 */
class CompanyDiscoverySchedulerTest {

    private DiscoverCompaniesUseCase discoverCompaniesUseCase;
    private CompanyProfilePort companyProfilePort;
    private CompanyEventPort companyEventPort;
    private CompanyProfileService companyProfileService;
    private CompanyDiscoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        discoverCompaniesUseCase = mock(DiscoverCompaniesUseCase.class);
        companyProfilePort = mock(CompanyProfilePort.class);
        companyEventPort = mock(CompanyEventPort.class);
        companyProfileService = mock(CompanyProfileService.class);
        scheduler = new CompanyDiscoveryScheduler(
                discoverCompaniesUseCase, companyProfilePort,
                companyEventPort, companyProfileService);
    }

    @Test
    void runDailyCompanyDiscovery_createsProfilesFromDiscoveryResults() {
        Company company = new Company("Tempus AI", "YC", "https://yc.com/tempus",
                "https://tempus.com", "Clinical data", CompanyTags.none(), true, true);
        CompanyDiscoveryResult result = new CompanyDiscoveryResult(
                List.of(company), "markdown", 10, 5, 3);

        when(discoverCompaniesUseCase.discover()).thenReturn(result);
        when(companyProfileService.toSlug("Tempus AI")).thenReturn("tempus-ai");
        when(companyProfilePort.findBySlug("tempus-ai")).thenReturn(Optional.empty());
        when(companyProfileService.upsertFromDiscovery(any(), anyList(), any()))
                .thenReturn(new CompanyProfile("tempus-ai", "Tempus AI", "https://tempus.com",
                        "Clinical data", List.of(), List.of(), Instant.now(), Instant.now(), 1, TrendDirection.NEW));
        when(companyProfileService.detectEvents(any(), anyList())).thenReturn(List.of());

        scheduler.runDailyCompanyDiscovery();

        verify(discoverCompaniesUseCase).discover();
        verify(companyProfilePort).save(any(CompanyProfile.class));
        verify(companyProfileService).upsertFromDiscovery(any(), anyList(), any());
    }

    @Test
    void runDailyCompanyDiscovery_exceptionCaught_schedulerSurvives() {
        when(discoverCompaniesUseCase.discover())
                .thenThrow(new RuntimeException("scrape failed"));

        // Should not throw
        scheduler.runDailyCompanyDiscovery();

        verify(discoverCompaniesUseCase).discover();
    }

    @Test
    void runDailyCompanyDiscovery_zeroResults_completesNormally() {
        CompanyDiscoveryResult result = new CompanyDiscoveryResult(
                List.of(), "", 0, 0, 0);
        when(discoverCompaniesUseCase.discover()).thenReturn(result);

        scheduler.runDailyCompanyDiscovery();

        verify(discoverCompaniesUseCase).discover();
        verify(companyProfilePort, never()).save(any());
    }

    @Test
    void runDailyCompanyDiscovery_updatesExistingProfile() {
        Company company = new Company("Aidoc", "TopStartups", "https://topstartups.io/aidoc",
                "https://aidoc.com", "Radiology AI", CompanyTags.none(), true, true);
        CompanyDiscoveryResult result = new CompanyDiscoveryResult(
                List.of(company), "markdown", 5, 3, 1);

        CompanyProfile existing = new CompanyProfile("aidoc", "Aidoc", "https://aidoc.com",
                "Radiology AI", List.of(), List.of("a1"), Instant.now(), Instant.now(), 1, TrendDirection.STABLE);

        when(discoverCompaniesUseCase.discover()).thenReturn(result);
        when(companyProfileService.toSlug("Aidoc")).thenReturn("aidoc");
        when(companyProfilePort.findBySlug("aidoc")).thenReturn(Optional.of(existing));
        when(companyProfileService.upsertFromDiscovery(any(), anyList(), any()))
                .thenReturn(existing);
        when(companyProfileService.detectEvents(any(), anyList())).thenReturn(List.of());

        scheduler.runDailyCompanyDiscovery();

        verify(companyProfilePort).save(any(CompanyProfile.class));
        verify(companyProfileService).upsertFromDiscovery(eq(company), anyList(), eq(existing));
    }
}
