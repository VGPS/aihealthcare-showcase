package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DigestDeliveryScheduler}.
 *
 * <p>Verifies the scheduler delegates to {@link DeliverNewsletterUseCase#deliverDigest()}
 * on each tick and that a failure is caught and logged rather than propagated, so the
 * scheduler thread survives for the next scheduled run.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-24
 * @updated 2026-08-24
 */
@ExtendWith(MockitoExtension.class)
class DigestDeliverySchedulerTest {

    @Mock
    private DeliverNewsletterUseCase deliverUseCase;

    private DigestDeliveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DigestDeliveryScheduler(deliverUseCase);
    }

    @Test
    void runDailyDigestDelivery_invokesDeliverDigest() {
        when(deliverUseCase.deliverDigest()).thenReturn(3);

        scheduler.runDailyDigestDelivery();

        verify(deliverUseCase).deliverDigest();
    }

    @Test
    void runDailyDigestDelivery_deliverDigestThrows_doesNotPropagate() {
        doThrow(new RuntimeException("digest delivery failure")).when(deliverUseCase).deliverDigest();

        assertThatCode(() -> scheduler.runDailyDigestDelivery()).doesNotThrowAnyException();
    }
}
