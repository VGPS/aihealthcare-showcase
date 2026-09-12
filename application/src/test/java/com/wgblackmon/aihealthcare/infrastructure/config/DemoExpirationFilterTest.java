package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DemoExpirationFilter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
class DemoExpirationFilterTest {

    private AppUserPort appUserPort;
    private SubscriberPort subscriberPort;
    private DemoExpirationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        appUserPort = mock(AppUserPort.class);
        subscriberPort = mock(SubscriberPort.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        TransactionTemplate transactionTemplate = new TransactionTemplate(txManager);
        filter = new DemoExpirationFilter(appUserPort, subscriberPort, transactionTemplate);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotFilter_skipsLoginPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_skipsChoosePathPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/choose-path");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_doesNotSkipDashboard() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void doFilter_expiredDemo_redirectsToChoosePath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        setAuthenticated("demo@example.com");
        AppUser expiredDemo = new AppUser("demo@example.com", "hash", "Demo", "USER", true,
                SubscriptionTier.DEMO, Instant.now().minus(1, ChronoUnit.DAYS));
        when(appUserPort.findByEmail("demo@example.com")).thenReturn(Optional.of(expiredDemo));
        when(subscriberPort.findByEmail("demo@example.com")).thenReturn(Optional.of(
                new Subscriber("demo@example.com", "Demo", true, Instant.now(), SubscriptionTier.DEMO, null, null, null)));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/choose-path");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_activeDemo_continuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        setAuthenticated("demo@example.com");
        AppUser activeDemo = new AppUser("demo@example.com", "hash", "Demo", "USER", true,
                SubscriptionTier.DEMO, Instant.now().plus(5, ChronoUnit.DAYS));
        when(appUserPort.findByEmail("demo@example.com")).thenReturn(Optional.of(activeDemo));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_freePendingUser_redirectsToChoosePath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        setAuthenticated("pending@example.com");
        AppUser pending = new AppUser("pending@example.com", "hash", "Pending", "USER", true,
                SubscriptionTier.FREE_PENDING, Instant.now().minus(1, ChronoUnit.DAYS));
        when(appUserPort.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/choose-path");
        verify(filterChain, never()).doFilter(any(), any());
    }

    private void setAuthenticated(String email) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                email, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
