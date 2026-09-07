package com.wgblackmon.aihealthcare.infrastructure.ingestion.web;

import com.wgblackmon.aihealthcare.domain.model.SourceCheckResult;
import com.wgblackmon.aihealthcare.domain.port.outbound.LawSourceMonitorPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * Infrastructure adapter implementing {@link LawSourceMonitorPort} via
 * the JDK {@link HttpClient}.
 *
 * <p>Fetches the given URL with a 15-second timeout, computes a SHA-256
 * hash of the response body, and compares it to the previously stored hash.
 * Returns a {@link SourceCheckResult} with the new hash, HTTP status, and
 * whether the content changed.
 *
 * <p>Error responses (HTTP 4xx/5xx) and network failures are captured
 * gracefully — they produce a result with {@code changed=true} so the
 * admin is alerted about unavailable sources.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
@Slf4j
@Component
public class HttpSourceMonitorAdapter implements LawSourceMonitorPort {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;

    public HttpSourceMonitorAdapter() {
        log.debug("HttpSourceMonitorAdapter()");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    HttpSourceMonitorAdapter(HttpClient httpClient) {
        log.debug("HttpSourceMonitorAdapter() | httpClient={}", httpClient);
        this.httpClient = httpClient;
    }

    @Override
    public SourceCheckResult checkUrl(String url, String storedHash) {
        log.debug("checkUrl() | url={}, storedHash={}", url, storedHash);
        Instant now = Instant.now();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "AIHealthcare-LegislationMonitor/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int status = response.statusCode();

            if (status >= 400) {
                boolean changed = storedHash != null;
                SourceCheckResult result = new SourceCheckResult(
                        status, null, changed, now,
                        "HTTP " + status + " error response");
                log.debug("checkUrl() | return={}", result);
                return result;
            }

            String body = response.body();
            String newHash = sha256(body);
            boolean changed = storedHash != null && !newHash.equals(storedHash);

            SourceCheckResult result = new SourceCheckResult(
                    status, newHash, changed, now, null);
            log.debug("checkUrl() | return={}", result);
            return result;

        } catch (Exception e) {
            log.warn("checkUrl() | failed url={}: {}", url, e.getMessage());
            boolean changed = storedHash != null;
            SourceCheckResult result = new SourceCheckResult(
                    0, null, changed, now, e.getMessage());
            log.debug("checkUrl() | return={}", result);
            return result;
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
