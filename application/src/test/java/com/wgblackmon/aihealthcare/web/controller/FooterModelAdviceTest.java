package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FooterModelAdvice} verifying the {@code footerTier}
 * model attribute is correctly resolved for different user states.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
@ExtendWith(MockitoExtension.class)
class FooterModelAdviceTest {

    @Mock
    private AppUserPort appUserPort;

    private FooterModelAdvice createAdvice(AppUserPort port) {
        @SuppressWarnings("unchecked")
        ObjectProvider<AppUserPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        return new FooterModelAdvice(provider);
    }

    @Test
    void anonymousUser_returnsAnonymous() {
        FooterModelAdvice advice = createAdvice(appUserPort);

        String result = advice.footerTier(null);

        assertThat(result).isEqualTo("ANONYMOUS");
    }

    @Test
    void noAppUserPort_returnsAnonymous() {
        FooterModelAdvice advice = createAdvice(null);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("user@example.com");

        String result = advice.footerTier(principal);

        assertThat(result).isEqualTo("ANONYMOUS");
    }

    @Test
    void freeUser_returnsFree() {
        FooterModelAdvice advice = createAdvice(appUserPort);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("free@example.com");
        AppUser user = new AppUser("free@example.com", "hash", "Free User",
                "USER", true, SubscriptionTier.FREE, null);
        when(appUserPort.findByEmail("free@example.com")).thenReturn(Optional.of(user));

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("free@example.com", null, List.of()));

        String result = advice.footerTier(principal);

        assertThat(result).isEqualTo("FREE");
        SecurityContextHolder.clearContext();
    }

    @Test
    void subscriberUser_returnsSubscriber() {
        FooterModelAdvice advice = createAdvice(appUserPort);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("sub@example.com");
        AppUser user = new AppUser("sub@example.com", "hash", "Sub User",
                "USER", true, SubscriptionTier.SUBSCRIBER, null);
        when(appUserPort.findByEmail("sub@example.com")).thenReturn(Optional.of(user));

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("sub@example.com", null, List.of()));

        String result = advice.footerTier(principal);

        assertThat(result).isEqualTo("SUBSCRIBER");
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminUser_alwaysReturnsSubscriber() {
        FooterModelAdvice advice = createAdvice(appUserPort);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("admin@example.com");
        AppUser user = new AppUser("admin@example.com", "hash", "Admin User",
                "ADMIN", true, SubscriptionTier.FREE, null);
        when(appUserPort.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        String result = advice.footerTier(principal);

        assertThat(result).isEqualTo("SUBSCRIBER");
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminUserWithEnterpriseTier_returnsEnterprise() {
        FooterModelAdvice advice = createAdvice(appUserPort);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("enterprise-admin@example.com");
        AppUser user = new AppUser("enterprise-admin@example.com", "hash", "Enterprise Admin",
                "ADMIN", true, SubscriptionTier.ENTERPRISE, null);
        when(appUserPort.findByEmail("enterprise-admin@example.com")).thenReturn(Optional.of(user));

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("enterprise-admin@example.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        String result = advice.footerTier(principal);

        assertThat(result).isEqualTo("ENTERPRISE");
        SecurityContextHolder.clearContext();
    }

    @Test
    void unknownUser_returnsFree() {
        FooterModelAdvice advice = createAdvice(appUserPort);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("unknown@example.com");
        when(appUserPort.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("unknown@example.com", null, List.of()));

        String result = advice.footerTier(principal);

        assertThat(result).isEqualTo("FREE");
        SecurityContextHolder.clearContext();
    }
}
