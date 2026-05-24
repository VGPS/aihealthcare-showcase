package com.wgblackmon.aihealthcare.application.service;

import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.service.DeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeliveryService#deliver(String)}.
 *
 * <p>Verifies the deliver pipeline: run lookup → active-subscriber fetch →
 * delivery dispatch → status update to SENT.  Also covers the no-subscribers
 * guard and the run-not-found error path.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
@ExtendWith(MockitoExtension.class)
class DeliveryServiceDeliverTest {

    @Mock
    private SubscriberPort         subscriberPort;
    @Mock
    private NewsletterRunPort      newsletterRunPort;
    @Mock
    private NewsletterDeliveryPort newsletterDeliveryPort;

    private DeliveryService service;

    private static final String RUN_ID = "run-001";

    private static final NewsletterRun DRAFT_RUN = new NewsletterRun(
            RUN_ID,
            "AI in Healthcare Weekly",
            LocalDate.of(2026, 4, 13),
            "<html><body>Content</body></html>",
            "Plain text content",
            NewsletterRunStatus.DRAFT,
            Instant.parse("2026-04-13T08:00:00Z")
    );

    private static final Subscriber SUBSCRIBER = new Subscriber(
            "jane@example.com", "Jane Doe", true, Instant.parse("2026-04-13T10:00:00Z"), null);

    @BeforeEach
    void setUp() {
        service = new DeliveryService(subscriberPort, newsletterRunPort, newsletterDeliveryPort);
    }

    // -------------------------------------------------------------------------
    // deliver() — happy path
    // -------------------------------------------------------------------------

    @Test
    void deliver_callsNewsletterDeliveryPortWithRunAndRecipients() {
        when(newsletterRunPort.findByRunId(RUN_ID)).thenReturn(DRAFT_RUN);
        when(subscriberPort.findAllActive()).thenReturn(List.of(SUBSCRIBER));

        service.deliver(RUN_ID);

        verify(newsletterDeliveryPort).deliver(DRAFT_RUN, List.of(SUBSCRIBER));
    }

    @Test
    void deliver_savesRunWithSentStatus() {
        when(newsletterRunPort.findByRunId(RUN_ID)).thenReturn(DRAFT_RUN);
        when(subscriberPort.findAllActive()).thenReturn(List.of(SUBSCRIBER));

        service.deliver(RUN_ID);

        ArgumentCaptor<NewsletterRun> captor = ArgumentCaptor.forClass(NewsletterRun.class);
        verify(newsletterRunPort).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(NewsletterRunStatus.SENT);
        assertThat(captor.getValue().runId()).isEqualTo(RUN_ID);
    }

    // -------------------------------------------------------------------------
    // deliver() — no active subscribers
    // -------------------------------------------------------------------------

    @Test
    void deliver_noActiveSubscribers_skipsDeliveryAndStatusUpdate() {
        when(newsletterRunPort.findByRunId(RUN_ID)).thenReturn(DRAFT_RUN);
        when(subscriberPort.findAllActive()).thenReturn(List.of());

        service.deliver(RUN_ID);

        verify(newsletterDeliveryPort, never()).deliver(any(), anyList());
        verify(newsletterRunPort, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // deliver() — run not found
    // -------------------------------------------------------------------------

    @Test
    void deliver_unknownRunId_throwsRunNotFoundException() {
        when(newsletterRunPort.findByRunId(RUN_ID))
                .thenThrow(new RunNotFoundException(RUN_ID));

        assertThatThrownBy(() -> service.deliver(RUN_ID))
                .isInstanceOf(RunNotFoundException.class);

        verify(newsletterDeliveryPort, never()).deliver(any(), anyList());
    }
}
