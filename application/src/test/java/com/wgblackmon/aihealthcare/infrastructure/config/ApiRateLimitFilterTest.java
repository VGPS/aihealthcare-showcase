package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.RateLimitResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApiRateLimitFilter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@ExtendWith(MockitoExtension.class)
class ApiRateLimitFilterTest {

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private FilterChain filterChain;

    private ApiRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiRateLimitFilter(rateLimiter);
    }

    @Test
    void doFilter_underLimit_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.addHeader("X-API-Key", "aih_test123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.tryAcquire(anyString()))
                .thenReturn(new RateLimitResult(true, 59, 0));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("59");
    }

    @Test
    void doFilter_overLimit_returns429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.addHeader("X-API-Key", "aih_test123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.tryAcquire(anyString()))
                .thenReturn(new RateLimitResult(false, 0, 30000));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_GONE + 19); // 429
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void doFilter_noApiKey_passesWithoutRateLimiting() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotFilter_nonApiPath_returnsTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}
