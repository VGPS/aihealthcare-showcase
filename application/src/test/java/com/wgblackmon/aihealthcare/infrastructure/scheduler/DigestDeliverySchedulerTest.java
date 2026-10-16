package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.PipelinePausePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DigestDeliveryScheduler}.
 *
 * <p>Verifies the scheduler delegates to {@link DeliverNewsletterUseCase#deliverDigest()}
 * on each tick, that a failure is caught and logged rather than propagated so the
 * scheduler thread survives for the next scheduled run, and that the pause kill
 * switch skips the tick entirely without calling deliverDigest().
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-24
 * @updated 2026-10-16
 */
@ExtendWith(MockitoExtension.class)
class DigestDeliverySchedulerTest {

    @Mock
    private DeliverNewsletterUseCase deliverUseCase;

    @Mock
    private PipelinePausePort pausePort;

    private DigestDeliveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DigestDeliveryScheduler(deliverUseCase, pausePort);
    }

    @Test
    void runDailyDigestDelivery_invokesDeliverDigest() {
        when(pausePort.isPaused("digest-send")).thenReturn(false);
        when(deliverUseCase.deliverDigest()).thenReturn(3);

        scheduler.runDailyDigestDelivery();

        verify(deliverUseCase).deliverDigest();
    }

    @Test
    void runDailyDigestDelivery_deliverDigestThrows_doesNotPropagate() {
        when(pausePort.isPaused("digest-send")).thenReturn(false);
        doThrow(new RuntimeException("digest delivery failure")).when(deliverUseCase).deliverDigest();

        assertThatCode(() -> scheduler.runDailyDigestDelivery()).doesNotThrowAnyException();
    }

    @Test
    void runDailyDigestDelivery_paused_skipsDeliverDigest() {
        when(pausePort.isPaused("digest-send")).thenReturn(true);

        scheduler.runDailyDigestDelivery();

        verify(deliverUseCase, never()).deliverDigest();
    }
}
