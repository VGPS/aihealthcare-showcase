package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.service.PerplexityCompanyDiscoveryService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CompanyDiscoveryScheduler}.
 *
 * <p>Verifies that the scheduler delegates to the Perplexity-powered discovery
 * service and survives exceptions without killing the scheduler thread.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-19
 * @updated 2026-08-02
 */
class CompanyDiscoverySchedulerTest {

    @Test
    void runWeeklyCompanyDiscovery_callsDiscoveryService() {
        PerplexityCompanyDiscoveryService discoveryService =
                mock(PerplexityCompanyDiscoveryService.class);
        when(discoveryService.runDiscoveryCycle()).thenReturn(5);

        CompanyDiscoveryScheduler scheduler = new CompanyDiscoveryScheduler(discoveryService);
        scheduler.runWeeklyCompanyDiscovery();

        verify(discoveryService).runDiscoveryCycle();
    }

    @Test
    void runWeeklyCompanyDiscovery_swallowsExceptions() {
        PerplexityCompanyDiscoveryService discoveryService =
                mock(PerplexityCompanyDiscoveryService.class);
        doThrow(new RuntimeException("API error")).when(discoveryService).runDiscoveryCycle();

        CompanyDiscoveryScheduler scheduler = new CompanyDiscoveryScheduler(discoveryService);
        // Should not throw — scheduler must survive
        scheduler.runWeeklyCompanyDiscovery();

        verify(discoveryService).runDiscoveryCycle();
    }

    @Test
    void runWeeklyCompanyDiscovery_zeroResults_completesNormally() {
        PerplexityCompanyDiscoveryService discoveryService =
                mock(PerplexityCompanyDiscoveryService.class);
        when(discoveryService.runDiscoveryCycle()).thenReturn(0);

        CompanyDiscoveryScheduler scheduler = new CompanyDiscoveryScheduler(discoveryService);
        scheduler.runWeeklyCompanyDiscovery();

        verify(discoveryService).runDiscoveryCycle();
    }
}
