package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.port.inbound.ManageStateLawsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LegislationMonitorScheduler}.
 *
 * <p>Verifies that the scheduler delegates to the use case and
 * survives exceptions without crashing the scheduler thread.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
class LegislationMonitorSchedulerTest {

    private ManageStateLawsUseCase useCase;
    private LegislationMonitorScheduler scheduler;

    @BeforeEach
    void setUp() {
        useCase = mock(ManageStateLawsUseCase.class);
        scheduler = new LegislationMonitorScheduler(useCase);
    }

    @Test
    void runSourceFreshnessCheck_delegatesToUseCase() {
        when(useCase.triggerRefresh()).thenReturn(3);

        scheduler.runSourceFreshnessCheck();

        verify(useCase).triggerRefresh();
    }

    @Test
    void runSourceFreshnessCheck_exceptionDoesNotPropagate() {
        doThrow(new RuntimeException("LLM error")).when(useCase).triggerRefresh();

        scheduler.runSourceFreshnessCheck();

        verify(useCase).triggerRefresh();
    }
}
