package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DemoExpirationScheduler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
class DemoExpirationSchedulerTest {

    private AppUserPort appUserPort;
    private SubscriberPort subscriberPort;
    private DemoExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        appUserPort = mock(AppUserPort.class);
        subscriberPort = mock(SubscriberPort.class);
        scheduler = new DemoExpirationScheduler(appUserPort, subscriberPort);
    }

    @Test
    void expireExpiredDemos_noExpiredUsers_doesNothing() {
        when(appUserPort.findByTierAndDemoExpiresAtBefore(eq("DEMO"), any()))
                .thenReturn(List.of());

        scheduler.expireExpiredDemos();

        verify(appUserPort, never()).save(any());
    }

    @Test
    void expireExpiredDemos_transitionsExpiredUserToFreePending() {
        AppUser expired = new AppUser("demo@example.com", "hash", "Demo", "USER", true,
                SubscriptionTier.DEMO, Instant.now().minus(1, ChronoUnit.DAYS));
        when(appUserPort.findByTierAndDemoExpiresAtBefore(eq("DEMO"), any()))
                .thenReturn(List.of(expired));
        when(subscriberPort.findByEmail("demo@example.com"))
                .thenReturn(Optional.of(new Subscriber("demo@example.com", "Demo", true,
                        Instant.now(), SubscriptionTier.DEMO)));

        scheduler.expireExpiredDemos();

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserPort).save(captor.capture());
        assertThat(captor.getValue().tier()).isEqualTo(SubscriptionTier.FREE_PENDING);
    }

    @Test
    void expireExpiredDemos_transitionsSubscriberToFreePending() {
        AppUser expired = new AppUser("demo@example.com", "hash", "Demo", "USER", true,
                SubscriptionTier.DEMO, Instant.now().minus(1, ChronoUnit.DAYS));
        when(appUserPort.findByTierAndDemoExpiresAtBefore(eq("DEMO"), any()))
                .thenReturn(List.of(expired));
        Subscriber sub = new Subscriber("demo@example.com", "Demo", true,
                Instant.now(), SubscriptionTier.DEMO);
        when(subscriberPort.findByEmail("demo@example.com")).thenReturn(Optional.of(sub));

        scheduler.expireExpiredDemos();

        ArgumentCaptor<Subscriber> captor = ArgumentCaptor.forClass(Subscriber.class);
        verify(subscriberPort).save(captor.capture());
        assertThat(captor.getValue().tier()).isEqualTo(SubscriptionTier.FREE_PENDING);
    }
}
