package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.CompanyDiscoveryResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.DiscoverCompaniesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CompanyDiscoveryScheduler}.
 *
 * <p>Verifies that the scheduler delegates to the discovery use case,
 * logs results, and survives exceptions without killing the thread.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-19
 * @updated 2026-07-19
 */
class CompanyDiscoverySchedulerTest {

    private DiscoverCompaniesUseCase discoverCompaniesUseCase;
    private CompanyDiscoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        discoverCompaniesUseCase = mock(DiscoverCompaniesUseCase.class);
        scheduler = new CompanyDiscoveryScheduler(discoverCompaniesUseCase);
    }

    @Test
    void runWeeklyCompanyDiscovery_delegatesToUseCase() {
        CompanyDiscoveryResult result = new CompanyDiscoveryResult(
                List.of(), "", 50, 30, 12);
        when(discoverCompaniesUseCase.discover()).thenReturn(result);

        scheduler.runWeeklyCompanyDiscovery();

        verify(discoverCompaniesUseCase).discover();
    }

    @Test
    void runWeeklyCompanyDiscovery_exceptionCaught_schedulerSurvives() {
        when(discoverCompaniesUseCase.discover())
                .thenThrow(new RuntimeException("scrape failed"));

        // Should not throw
        scheduler.runWeeklyCompanyDiscovery();

        verify(discoverCompaniesUseCase).discover();
    }

    @Test
    void runWeeklyCompanyDiscovery_zeroResults_completesNormally() {
        CompanyDiscoveryResult result = new CompanyDiscoveryResult(
                List.of(), "", 0, 0, 0);
        when(discoverCompaniesUseCase.discover()).thenReturn(result);

        scheduler.runWeeklyCompanyDiscovery();

        verify(discoverCompaniesUseCase).discover();
    }
}
