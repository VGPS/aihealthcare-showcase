package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.AnalystNote;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NoteTargetType;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanySentimentPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DailyBriefingService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@ExtendWith(MockitoExtension.class)
class DailyBriefingServiceTest {

    @Mock private SubscriberPort subscriberPort;
    @Mock private WatchlistPort watchlistPort;
    @Mock private WatchlistMatchPort watchlistMatchPort;
    @Mock private CompanySentimentPort companySentimentPort;
    @Mock private AnalystNotePort analystNotePort;
    @Mock private NewsletterDeliveryPort newsletterDeliveryPort;

    private DailyBriefingService service;
    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    @BeforeEach
    void setUp() {
        DailyBriefingRenderer renderer = new DailyBriefingRenderer();
        service = new DailyBriefingService(
                subscriberPort, watchlistPort, watchlistMatchPort,
                companySentimentPort, analystNotePort,
                newsletterDeliveryPort, renderer, 24);
    }

    private Subscriber buildSubscriber(String email, String name, SubscriptionTier tier) {
        return new Subscriber(email, name, true, NOW, tier, "token-" + email, null, null);
    }

    private WatchlistItem buildItem(String id, String email, WatchlistItemType type, String value) {
        return new WatchlistItem(id, email, type, value, value, NOW);
    }

    @Test
    void sendDailyBriefings_subscriberWithWatchlist_sendsPersonalizedEmail() {
        Subscriber sub = buildSubscriber("user@test.com", "Test User", SubscriptionTier.SUBSCRIBER);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.SUBSCRIBER)).thenReturn(List.of(sub));
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.DEMO)).thenReturn(List.of());

        WatchlistItem item = buildItem("i1", "user@test.com", WatchlistItemType.COMPANY, "tempus-ai");
        when(watchlistPort.findByUser("user@test.com")).thenReturn(List.of(item));
        when(watchlistMatchPort.findByUserSince(eq("user@test.com"), any())).thenReturn(
                List.of(new WatchlistMatch("m1", "i1", "art-1", NOW, "snippet")));
        when(companySentimentPort.findBySlug("tempus-ai")).thenReturn(
                Optional.of(new CompanySentiment("tempus-ai", "Tempus AI",
                        SentimentLabel.POSITIVE, 0.5, 10, 5, 2, 2, 1,
                        "Good outlook", List.of(), NOW)));
        when(analystNotePort.findByUserSince(eq("user@test.com"), any())).thenReturn(List.of());

        service.sendDailyBriefings();

        verify(newsletterDeliveryPort).deliver(any(NewsletterRun.class), eq(List.of(sub)));
    }

    @Test
    void sendDailyBriefings_demoTierIncluded_sendsToDemo() {
        Subscriber demo = buildSubscriber("demo@test.com", "Demo User", SubscriptionTier.DEMO);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.SUBSCRIBER)).thenReturn(List.of());
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.DEMO)).thenReturn(List.of(demo));

        when(watchlistPort.findByUser("demo@test.com")).thenReturn(
                List.of(buildItem("i1", "demo@test.com", WatchlistItemType.KEYWORD, "AI")));
        when(watchlistMatchPort.findByUserSince(eq("demo@test.com"), any())).thenReturn(List.of());
        when(analystNotePort.findByUserSince(eq("demo@test.com"), any())).thenReturn(List.of());

        service.sendDailyBriefings();

        verify(newsletterDeliveryPort).deliver(any(NewsletterRun.class), eq(List.of(demo)));
    }

    @Test
    void sendDailyBriefings_noWatchlistAndNoNotes_skipsSubscriber() {
        Subscriber sub = buildSubscriber("user@test.com", "Test User", SubscriptionTier.SUBSCRIBER);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.SUBSCRIBER)).thenReturn(List.of(sub));
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.DEMO)).thenReturn(List.of());

        when(watchlistPort.findByUser("user@test.com")).thenReturn(List.of());
        when(analystNotePort.findByUserSince(eq("user@test.com"), any())).thenReturn(List.of());

        service.sendDailyBriefings();

        verify(newsletterDeliveryPort, never()).deliver(any(), any());
    }

    @Test
    void sendDailyBriefings_subscriberHasNotesOnly_sendsEmail() {
        Subscriber sub = buildSubscriber("user@test.com", "Test User", SubscriptionTier.SUBSCRIBER);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.SUBSCRIBER)).thenReturn(List.of(sub));
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.DEMO)).thenReturn(List.of());

        when(watchlistPort.findByUser("user@test.com")).thenReturn(List.of());
        when(analystNotePort.findByUserSince(eq("user@test.com"), any())).thenReturn(
                List.of(new AnalystNote("n1", "user@test.com", NoteTargetType.COMPANY,
                        "tempus-ai", "Tempus AI", "Note content", NOW, NOW)));

        service.sendDailyBriefings();

        verify(newsletterDeliveryPort).deliver(any(NewsletterRun.class), eq(List.of(sub)));
    }

    @Test
    void sendDailyBriefings_subscriberHasWatchlistOnly_sendsEmail() {
        Subscriber sub = buildSubscriber("user@test.com", "Test User", SubscriptionTier.SUBSCRIBER);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.SUBSCRIBER)).thenReturn(List.of(sub));
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.DEMO)).thenReturn(List.of());

        when(watchlistPort.findByUser("user@test.com")).thenReturn(
                List.of(buildItem("i1", "user@test.com", WatchlistItemType.KEYWORD, "AI")));
        when(watchlistMatchPort.findByUserSince(eq("user@test.com"), any())).thenReturn(List.of());
        when(analystNotePort.findByUserSince(eq("user@test.com"), any())).thenReturn(List.of());

        service.sendDailyBriefings();

        verify(newsletterDeliveryPort).deliver(any(NewsletterRun.class), eq(List.of(sub)));
    }

    @Test
    void sendDailyBriefings_oneSubscriberFails_continuesWithNext() {
        Subscriber sub1 = buildSubscriber("fail@test.com", "Fail User", SubscriptionTier.SUBSCRIBER);
        Subscriber sub2 = buildSubscriber("ok@test.com", "OK User", SubscriptionTier.SUBSCRIBER);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.SUBSCRIBER)).thenReturn(List.of(sub1, sub2));
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.DEMO)).thenReturn(List.of());

        // First subscriber's watchlist lookup throws
        when(watchlistPort.findByUser("fail@test.com")).thenThrow(new RuntimeException("DB error"));

        // Second subscriber succeeds
        when(watchlistPort.findByUser("ok@test.com")).thenReturn(
                List.of(buildItem("i1", "ok@test.com", WatchlistItemType.KEYWORD, "AI")));
        when(watchlistMatchPort.findByUserSince(eq("ok@test.com"), any())).thenReturn(List.of());
        when(analystNotePort.findByUserSince(eq("ok@test.com"), any())).thenReturn(List.of());

        service.sendDailyBriefings();

        verify(newsletterDeliveryPort).deliver(any(NewsletterRun.class), eq(List.of(sub2)));
    }

    @Test
    void sendDailyBriefings_noActiveSubscribers_doesNothing() {
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.SUBSCRIBER)).thenReturn(List.of());
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.DEMO)).thenReturn(List.of());

        service.sendDailyBriefings();

        verify(newsletterDeliveryPort, never()).deliver(any(), any());
    }

    @Test
    void sendDailyBriefings_sentimentLookup_onlyForCompanyItems() {
        Subscriber sub = buildSubscriber("user@test.com", "Test User", SubscriptionTier.SUBSCRIBER);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.SUBSCRIBER)).thenReturn(List.of(sub));
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.DEMO)).thenReturn(List.of());

        when(watchlistPort.findByUser("user@test.com")).thenReturn(List.of(
                buildItem("i1", "user@test.com", WatchlistItemType.KEYWORD, "AI"),
                buildItem("i2", "user@test.com", WatchlistItemType.TOPIC, "FDA"),
                buildItem("i3", "user@test.com", WatchlistItemType.COMPANY, "tempus-ai")));
        when(watchlistMatchPort.findByUserSince(eq("user@test.com"), any())).thenReturn(List.of());
        when(analystNotePort.findByUserSince(eq("user@test.com"), any())).thenReturn(List.of());
        when(companySentimentPort.findBySlug("tempus-ai")).thenReturn(Optional.empty());

        service.sendDailyBriefings();

        // Only called for the COMPANY item, not KEYWORD or TOPIC
        verify(companySentimentPort, times(1)).findBySlug("tempus-ai");
        verify(companySentimentPort, never()).findBySlug("AI");
        verify(companySentimentPort, never()).findBySlug("FDA");
    }

    @Test
    void sendDailyBriefings_syntheticRunHasBriefingPrefix() {
        Subscriber sub = buildSubscriber("user@test.com", "Test User", SubscriptionTier.SUBSCRIBER);
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.SUBSCRIBER)).thenReturn(List.of(sub));
        when(subscriberPort.findAllActiveByTier(SubscriptionTier.DEMO)).thenReturn(List.of());

        when(watchlistPort.findByUser("user@test.com")).thenReturn(
                List.of(buildItem("i1", "user@test.com", WatchlistItemType.KEYWORD, "AI")));
        when(watchlistMatchPort.findByUserSince(eq("user@test.com"), any())).thenReturn(List.of());
        when(analystNotePort.findByUserSince(eq("user@test.com"), any())).thenReturn(List.of());

        service.sendDailyBriefings();

        ArgumentCaptor<NewsletterRun> runCaptor = ArgumentCaptor.forClass(NewsletterRun.class);
        verify(newsletterDeliveryPort).deliver(runCaptor.capture(), any());

        NewsletterRun deliveredRun = runCaptor.getValue();
        assertThat(deliveredRun.runId()).startsWith("briefing-");
        assertThat(deliveredRun.title()).isEqualTo("Your Daily AI Healthcare Briefing");
    }
}
