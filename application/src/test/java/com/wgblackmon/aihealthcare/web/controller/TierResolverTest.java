package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TierResolver}, verifying tier resolution from
 * {@link Principal} and email, plus admin authority checks.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-11
 * @updated 2026-09-11
 */
@ExtendWith(MockitoExtension.class)
class TierResolverTest {

    @Mock
    private SubscriberPort subscriberPort;

    private TierResolver tierResolver;

    @BeforeEach
    void setUp() {
        tierResolver = new TierResolver(subscriberPort);
    }

    @Test
    void resolveTier_nullPrincipal_returnsFree() {
        SubscriptionTier result = tierResolver.resolveTier((Principal) null);
        assertThat(result).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    void resolveTier_adminPrincipal_returnsSubscriber() {
        Authentication admin = new UsernamePasswordAuthenticationToken(
                "admin@test.com", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        SubscriptionTier result = tierResolver.resolveTier(admin);
        assertThat(result).isEqualTo(SubscriptionTier.SUBSCRIBER);
    }

    @Test
    void resolveTier_subscriberFound_returnsTier() {
        Authentication user = new UsernamePasswordAuthenticationToken(
                "user@test.com", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        Subscriber subscriber = new Subscriber(
                "user@test.com", "User", true, null,
                SubscriptionTier.DEMO, null, null, null);
        when(subscriberPort.findByEmail("user@test.com"))
                .thenReturn(Optional.of(subscriber));

        SubscriptionTier result = tierResolver.resolveTier(user);
        assertThat(result).isEqualTo(SubscriptionTier.DEMO);
    }

    @Test
    void resolveTier_noSubscriber_returnsFree() {
        Authentication user = new UsernamePasswordAuthenticationToken(
                "unknown@test.com", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(subscriberPort.findByEmail("unknown@test.com"))
                .thenReturn(Optional.empty());

        SubscriptionTier result = tierResolver.resolveTier(user);
        assertThat(result).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    void resolveTier_byEmail_returnsFoundTier() {
        Subscriber subscriber = new Subscriber(
                "sub@test.com", "Sub", true, null,
                SubscriptionTier.SUBSCRIBER, null, null, null);
        when(subscriberPort.findByEmail("sub@test.com"))
                .thenReturn(Optional.of(subscriber));

        SubscriptionTier result = tierResolver.resolveTier("sub@test.com");
        assertThat(result).isEqualTo(SubscriptionTier.SUBSCRIBER);
    }

    @Test
    void resolveTier_byEmail_null_returnsFree() {
        SubscriptionTier result = tierResolver.resolveTier((String) null);
        assertThat(result).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    void isAdmin_withAdminRole_returnsTrue() {
        Authentication admin = new UsernamePasswordAuthenticationToken(
                "admin@test.com", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(tierResolver.isAdmin(admin)).isTrue();
    }

    @Test
    void isAdmin_withUserRole_returnsFalse() {
        Authentication user = new UsernamePasswordAuthenticationToken(
                "user@test.com", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThat(tierResolver.isAdmin(user)).isFalse();
    }

    @Test
    void isAdmin_nullPrincipal_returnsFalse() {
        assertThat(tierResolver.isAdmin(null)).isFalse();
    }
}
