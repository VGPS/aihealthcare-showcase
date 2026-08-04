package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.service.DailyBriefingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DailyBriefingScheduler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@ExtendWith(MockitoExtension.class)
class DailyBriefingSchedulerTest {

    @Mock
    private DailyBriefingService dailyBriefingService;

    @InjectMocks
    private DailyBriefingScheduler scheduler;

    @Test
    void runDailyBriefing_delegatesToService() {
        scheduler.runDailyBriefing();
        verify(dailyBriefingService).sendDailyBriefings();
    }

    @Test
    void runDailyBriefing_serviceThrows_doesNotPropagate() {
        doThrow(new RuntimeException("Pipeline failure"))
                .when(dailyBriefingService).sendDailyBriefings();

        scheduler.runDailyBriefing();

        verify(dailyBriefingService).sendDailyBriefings();
    }
}
