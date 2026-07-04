package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.ApiKey;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

/**
 * Servlet filter that authenticates REST API requests via the {@code X-API-Key}
 * header.
 *
 * <p>Only applies to {@code /api/**} paths. If the header is present, the raw
 * key is SHA-256 hashed and looked up via {@link ApiKeyPort}. A match sets a
 * Spring Security authentication token for the request scope. If the header
 * is absent, the filter passes through (allowing existing session/form auth
 * or permitAll to handle it).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-API-Key";

    private final ApiKeyPort apiKeyPort;

    public ApiKeyAuthenticationFilter(ApiKeyPort apiKeyPort) {
        log.debug("ApiKeyAuthenticationFilter() | apiKeyPort={}", apiKeyPort.getClass().getSimpleName());
        this.apiKeyPort = apiKeyPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        log.debug("doFilterInternal() | uri={}, method={}", request.getRequestURI(), request.getMethod());

        String apiKeyHeader = request.getHeader(HEADER_NAME);

        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            String keyHash = sha256(apiKeyHeader);
            Optional<ApiKey> keyOpt = apiKeyPort.findByKeyHash(keyHash);

            if (keyOpt.isPresent() && keyOpt.get().active()) {
                ApiKey key = keyOpt.get();
                log.debug("doFilterInternal() | authenticated via API key, owner={}", key.ownerEmail());

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                key.ownerEmail(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                log.debug("doFilterInternal() | invalid or inactive API key");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Invalid API key\"}");
                return;
            }
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

    /**
     * Computes the SHA-256 hex digest of the given raw key.
     */
    public static String sha256(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
