package com.wgblackmon.aihealthcare.infrastructure.ingestion.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks whether a URL is allowed to be fetched based on the target domain's
 * {@code robots.txt} rules and a configurable domain denylist.
 *
 * <p>Each domain's {@code robots.txt} is fetched once and cached for 24 hours.
 * The parser checks Disallow directives under both the {@code AIHealthcare-Monitor}
 * user-agent and the wildcard {@code *} user-agent. The denylist always wins:
 * a denied domain is blocked regardless of its {@code robots.txt} content.
 *
 * <p>Scoped to {@link WebPageHarvester} only — RSS feeds and API harvesters
 * do not need robots.txt gating.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
@Slf4j
@Component
public class RobotsTxtGate {

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String USER_AGENT = "aihealthcare-monitor";

    private final Set<String> denylist;
    private final Map<String, CachedRobotsTxt> cache = new ConcurrentHashMap<>();

    public RobotsTxtGate(@Value("${aihealthcare.ingestion.domain-denylist:}") List<String> denylist) {
        log.debug("RobotsTxtGate() | denylist={}", denylist);
        this.denylist = Set.copyOf(denylist != null ? denylist : List.of());
    }

    /**
     * Returns {@code true} if the given URL is allowed to be fetched.
     *
     * @param url the URL to check
     * @return {@code true} if allowed, {@code false} if blocked by denylist or robots.txt
     */
    public boolean isAllowed(String url) {
        log.debug("isAllowed() | url={}", url);

        String domain;
        String path;
        try {
            URI uri = URI.create(url);
            domain = uri.getHost();
            path = uri.getPath();
            if (domain == null) {
                log.debug("isAllowed() | return=true (no host in URL)");
                return true;
            }
            if (path == null || path.isEmpty()) {
                path = "/";
            }
        } catch (Exception e) {
            log.warn("isAllowed() | failed to parse URL '{}': {}", url, e.getMessage());
            log.debug("isAllowed() | return=true (parse failure, fail open)");
            return true;
        }

        if (denylist.contains(domain.toLowerCase())) {
            log.warn("isAllowed() | domain '{}' is on the denylist — blocking", domain);
            log.debug("isAllowed() | return=false");
            return false;
        }

        List<String> disallowedPaths = getDisallowedPaths(domain);
        for (String disallowed : disallowedPaths) {
            if (path.startsWith(disallowed)) {
                log.warn("isAllowed() | path '{}' is disallowed by robots.txt for domain '{}'",
                         path, domain);
                log.debug("isAllowed() | return=false");
                return false;
            }
        }

        log.debug("isAllowed() | return=true");
        return true;
    }

    private List<String> getDisallowedPaths(String domain) {
        log.debug("getDisallowedPaths() | domain={}", domain);

        CachedRobotsTxt cached = cache.get(domain);
        if (cached != null && cached.fetchedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            log.debug("getDisallowedPaths() | return=cached ({} paths)", cached.disallowedPaths().size());
            return cached.disallowedPaths();
        }

        List<String> paths = fetchAndParseRobotsTxt(domain);
        cache.put(domain, new CachedRobotsTxt(paths, Instant.now()));
        log.debug("getDisallowedPaths() | return=fresh ({} paths)", paths.size());
        return paths;
    }

    List<String> fetchAndParseRobotsTxt(String domain) {
        log.debug("fetchAndParseRobotsTxt() | domain={}", domain);
        try {
            URL robotsUrl = new URL("https://" + domain + "/robots.txt");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) robotsUrl.openConnection();
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setRequestProperty("User-Agent", "AIHealthcare-Monitor/1.0");

            int status = conn.getResponseCode();
            if (status != 200) {
                log.debug("fetchAndParseRobotsTxt() | robots.txt returned {}, allowing all", status);
                log.debug("fetchAndParseRobotsTxt() | return=empty");
                return List.of();
            }

            String body;
            try (var is = conn.getInputStream()) {
                body = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            List<String> result = parseRobotsTxt(body);
            log.debug("fetchAndParseRobotsTxt() | return={} disallowed paths", result.size());
            return result;
        } catch (Exception e) {
            log.debug("fetchAndParseRobotsTxt() | failed to fetch robots.txt for '{}': {}", domain, e.getMessage());
            log.debug("fetchAndParseRobotsTxt() | return=empty (fail open)");
            return List.of();
        }
    }

    private List<String> parseRobotsTxt(String body) {
        log.debug("parseRobotsTxt() | body.length={}", body.length());

        List<String> disallowed = new ArrayList<>();
        boolean inRelevantBlock = false;

        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.toLowerCase().startsWith("user-agent:")) {
                String agent = trimmed.substring("user-agent:".length()).trim().toLowerCase();
                inRelevantBlock = agent.equals("*") || agent.equals(USER_AGENT);
            } else if (inRelevantBlock && trimmed.toLowerCase().startsWith("disallow:")) {
                String path = trimmed.substring("disallow:".length()).trim();
                if (!path.isEmpty()) {
                    disallowed.add(path);
                }
            }
        }

        List<String> result = Collections.unmodifiableList(disallowed);
        log.debug("parseRobotsTxt() | return={} disallowed paths", result.size());
        return result;
    }

    record CachedRobotsTxt(List<String> disallowedPaths, Instant fetchedAt) {}
}
