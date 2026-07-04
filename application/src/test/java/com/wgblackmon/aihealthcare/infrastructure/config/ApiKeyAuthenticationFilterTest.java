package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.ApiKey;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApiKeyAuthenticationFilter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
class ApiKeyAuthenticationFilterTest {

    private ApiKeyPort apiKeyPort;
    private ApiKeyAuthenticationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        apiKeyPort = mock(ApiKeyPort.class);
        filter = new ApiKeyAuthenticationFilter(apiKeyPort);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Valid API key sets authentication context")
    void validApiKey_setsAuthentication() throws Exception {
        String rawKey = "aih_test1234567890abcdef";
        String keyHash = ApiKeyAuthenticationFilter.sha256(rawKey);
        ApiKey apiKey = new ApiKey("key-1", "user@test.com", "Test Key", "aih_test",
                                  keyHash, true, Instant.now());
        when(apiKeyPort.findByKeyHash(keyHash)).thenReturn(Optional.of(apiKey));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.addHeader("X-API-Key", rawKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("user@test.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Invalid API key returns 401")
    void invalidApiKey_returns401() throws Exception {
        when(apiKeyPort.findByKeyHash(anyString())).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.addHeader("X-API-Key", "aih_invalid_key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Inactive API key returns 401")
    void inactiveApiKey_returns401() throws Exception {
        String rawKey = "aih_inactive_key_12345";
        String keyHash = ApiKeyAuthenticationFilter.sha256(rawKey);
        ApiKey apiKey = new ApiKey("key-2", "user@test.com", "Inactive", "aih_inac",
                                  keyHash, false, Instant.now());
        when(apiKeyPort.findByKeyHash(keyHash)).thenReturn(Optional.of(apiKey));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.addHeader("X-API-Key", rawKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("No API key header passes through")
    void noApiKeyHeader_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Non-API path is skipped")
    void nonApiPath_isSkipped() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("sha256 produces consistent hex digest")
    void sha256_producesConsistentDigest() {
        String hash1 = ApiKeyAuthenticationFilter.sha256("test-key");
        String hash2 = ApiKeyAuthenticationFilter.sha256("test-key");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 = 32 bytes = 64 hex chars
    }
}
