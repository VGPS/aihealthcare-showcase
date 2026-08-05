package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.RateLimitResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that enforces per-key rate limiting on API requests.
 *
 * <p>Only rate-limits requests that carry an {@code X-API-Key} header.
 * Session-authenticated requests (browser AJAX calls) pass through
 * without rate limiting.
 *
 * <p>Registered via {@code FilterRegistrationBean} in
 * {@link AppConfig} to avoid changing {@link SecurityConfig}'s constructor.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-API-Key";

    private final RateLimiter rateLimiter;

    public ApiRateLimitFilter(RateLimiter rateLimiter) {
        log.debug("ApiRateLimitFilter() | rateLimiter={}", rateLimiter.getClass().getSimpleName());
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        log.debug("doFilterInternal() | uri={}, method={}", request.getRequestURI(), request.getMethod());

        String apiKeyHeader = request.getHeader(HEADER_NAME);

        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            String keyHash = ApiKeyAuthenticationFilter.sha256(apiKeyHeader);
            RateLimitResult result = rateLimiter.tryAcquire(keyHash);

            if (!result.allowed()) {
                log.debug("doFilterInternal() | rate limited, retryAfterMillis={}", result.retryAfterMillis());
                response.setStatus(429);
                response.setContentType("application/json");
                long retryAfterSeconds = Math.max(1, result.retryAfterMillis() / 1000);
                response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Rate limit exceeded. "
                        + "Retry after " + retryAfterSeconds + " seconds\",\"retryAfterSeconds\":"
                        + retryAfterSeconds + "}");
                return;
            }

            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingRequests()));
        }

        filterChain.doFilter(request, response);
        log.debug("doFilterInternal() | return=void");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean skip = !path.startsWith("/api/");
        log.debug("shouldNotFilter() | path={}, skip={}", path, skip);
        return skip;
    }
}
