package com.wgblackmon.aihealthcare.application.service;

import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.service.DeliveryService;
import com.wgblackmon.aihealthcare.domain.service.NewsletterTeaserBuilder;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeliveryService#deliver(String)}.
 *
 * <p>Verifies the tier-aware deliver pipeline: run lookup → split subscribers by tier →
 * full content to PREMIUM/ENTERPRISE → teaser to FREE → status update to SENT.
 * Also covers the no-subscribers guard and the run-not-found error path.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-05-24
 */
@ExtendWith(MockitoExtension.class)
class DeliveryServiceDeliverTest {

    @Mock
    private SubscriberPort         subscriberPort;
    @Mock
    private NewsletterRunPort      newsletterRunPort;
    @Mock
    private NewsletterDeliveryPort newsletterDeliveryPort;
    @Mock
    private NewsletterTeaserBuilder teaserBuilder;

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

    private static final NewsletterRun TEASER_RUN = new NewsletterRun(
            RUN_ID,
            "AI in Healthcare Weekly",
            LocalDate.of(2026, 4, 13),
            "<html><body>Teaser</body></html>",
            "Teaser text",
            NewsletterRunStatus.DRAFT,
            Instant.parse("2026-04-13T08:00:00Z")
    );

    private static final Subscriber PREMIUM_SUB = new Subscriber(
            "premium@example.com", "Premium User", true, Instant.parse("2026-04-13T10:00:00Z"), SubscriptionTier.PREMIUM);

    private static final Subscriber ENTERPRISE_SUB = new Subscriber(
            "enterprise@example.com", "Enterprise User", true, Instant.parse("2026-04-13T10:00:00Z"), SubscriptionTier.ENTERPRISE);

    private static final Subscriber FREE_SUB = new Subscriber(
            "free@example.com", "Free User", true, Instant.parse("2026-04-13T10:00:00Z"), SubscriptionTier.FREE);

    @BeforeEach
    void setUp() {
        service = new DeliveryService(subscriberPort, newsletterRunPort, newsletterDeliveryPort, teaserBuilder);
    }

    // -------------------------------------------------------------------------
    // deliver() — tier-aware happy path
    // -------------------------------------------------------------------------

    @Test
    void deliver_sendsFullContentToPaidSubscribers() {
        when(newsletterRunPort.findByRunId(RUN_ID)).thenReturn(DRAFT_RUN);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.PREMIUM)).thenReturn(List.of(PREMIUM_SUB));
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.ENTERPRISE)).thenReturn(List.of(ENTERPRISE_SUB));
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.FREE)).thenReturn(List.of());

        service.deliver(RUN_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Subscriber>> captor = ArgumentCaptor.forClass(List.class);
        verify(newsletterDeliveryPort).deliver(any(NewsletterRun.class), captor.capture());
        List<Subscriber> paidRecipients = captor.getValue();
        assertThat(paidRecipients).hasSize(2);
        assertThat(paidRecipients).extracting(Subscriber::email)
                .containsExactly("premium@example.com", "enterprise@example.com");
    }

    @Test
    void deliver_sendsTeaserToFreeSubscribers() {
        when(newsletterRunPort.findByRunId(RUN_ID)).thenReturn(DRAFT_RUN);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.PREMIUM)).thenReturn(List.of());
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.ENTERPRISE)).thenReturn(List.of());
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.FREE)).thenReturn(List.of(FREE_SUB));
        when(teaserBuilder.buildTeaser(DRAFT_RUN)).thenReturn(TEASER_RUN);

        service.deliver(RUN_ID);

        verify(teaserBuilder).buildTeaser(DRAFT_RUN);
        verify(newsletterDeliveryPort).deliver(TEASER_RUN, List.of(FREE_SUB));
    }

    @Test
    void deliver_mixedTiers_sendsBothFullAndTeaser() {
        when(newsletterRunPort.findByRunId(RUN_ID)).thenReturn(DRAFT_RUN);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.PREMIUM)).thenReturn(List.of(PREMIUM_SUB));
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.ENTERPRISE)).thenReturn(List.of());
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.FREE)).thenReturn(List.of(FREE_SUB));
        when(teaserBuilder.buildTeaser(DRAFT_RUN)).thenReturn(TEASER_RUN);

        service.deliver(RUN_ID);

        // Two deliver calls: one for paid, one for free
        verify(newsletterDeliveryPort, times(2)).deliver(any(NewsletterRun.class), anyList());
        verify(teaserBuilder).buildTeaser(DRAFT_RUN);
    }

    @Test
    void deliver_savesRunWithSentStatus() {
        when(newsletterRunPort.findByRunId(RUN_ID)).thenReturn(DRAFT_RUN);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.PREMIUM)).thenReturn(List.of(PREMIUM_SUB));
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.ENTERPRISE)).thenReturn(List.of());
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.FREE)).thenReturn(List.of());

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
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.PREMIUM)).thenReturn(List.of());
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.ENTERPRISE)).thenReturn(List.of());
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.FREE)).thenReturn(List.of());

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
