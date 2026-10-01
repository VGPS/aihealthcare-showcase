package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.port.outbound.SearchEngineNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Pings the <a href="https://www.indexnow.org/">IndexNow</a> API whenever wiki
 * pages, companies, or state laws are created or updated, so Bing (and other
 * IndexNow-participating engines) crawl new content faster than waiting on the
 * next scheduled sitemap fetch. Google does not consume IndexNow.
 *
 * <p>The key is a public, non-secret token proving domain ownership — it is
 * served back verbatim at {@code GET /{key}.txt} by {@link
 * com.wgblackmon.aihealthcare.web.controller.SeoController}.
 *
 * <p>Disabled by default (see {@code aihealthcare.indexnow.enabled} in
 * {@code application.yml}) so test and dev runs never make real outbound
 * calls; {@code application-aws.yml} turns it on for production. The actual
 * HTTP call runs on a dedicated single-thread executor so a slow or failing
 * IndexNow request never blocks the create/update operation that triggered it.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-30
 * @updated 2026-09-30
 */
@Slf4j
@Component
public class IndexNowAdapter implements SearchEngineNotificationPort {

    private static final String INDEXNOW_URL = "https://api.indexnow.org/indexnow";

    private final RestClient restClient;
    private final Executor executor;
    private final String host;
    private final String key;
    private final String keyLocation;
    private final boolean enabled;

    @Autowired
    public IndexNowAdapter(@Value("${aihealthcare.base-url}") String baseUrl,
                            @Value("${aihealthcare.indexnow.key}") String key,
                            @Value("${aihealthcare.indexnow.enabled:false}") boolean enabled) {
        this(RestClient.builder().build(), Executors.newSingleThreadExecutor(), baseUrl, key, enabled);
    }

    /** Package-private constructor for unit testing — accepts injectable collaborators (e.g. a same-thread Executor). */
    IndexNowAdapter(RestClient restClient, Executor executor,
                    String baseUrl, String key, boolean enabled) {
        log.debug("IndexNowAdapter() | baseUrl={}, enabled={}", baseUrl, enabled);
        this.restClient = restClient;
        this.executor = executor;
        this.host = URI.create(baseUrl).getHost();
        this.key = key;
        this.keyLocation = baseUrl + "/" + key + ".txt";
        this.enabled = enabled;
        log.debug("IndexNowAdapter() | return=void");
    }

    @Override
    public void notifyUrlsChanged(List<String> absoluteUrls) {
        log.debug("notifyUrlsChanged() | enabled={}, urlCount={}", enabled, absoluteUrls.size());
        if (!enabled || absoluteUrls.isEmpty()) {
            log.debug("notifyUrlsChanged() | return=void (skipped)");
            return;
        }
        List<String> urls = List.copyOf(absoluteUrls);
        executor.execute(() -> postToIndexNow(urls));
        log.debug("notifyUrlsChanged() | return=void (queued)");
    }

    private void postToIndexNow(List<String> urls) {
        log.debug("postToIndexNow() | urlCount={}", urls.size());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("host", host);
        body.put("key", key);
        body.put("keyLocation", keyLocation);
        body.put("urlList", urls);
        try {
            restClient.post()
                    .uri(INDEXNOW_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("postToIndexNow() | notified {} URL(s)", urls.size());
        } catch (Exception e) {
            log.warn("postToIndexNow() | failed to notify IndexNow: {}", e.getMessage());
        }
        log.debug("postToIndexNow() | return=void");
    }
}
