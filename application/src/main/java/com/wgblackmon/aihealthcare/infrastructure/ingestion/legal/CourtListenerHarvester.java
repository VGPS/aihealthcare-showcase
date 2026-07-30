package com.wgblackmon.aihealthcare.infrastructure.ingestion.legal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Harvests court opinions related to AI in healthcare from the
 * CourtListener REST API.
 *
 * <p>Queries {@code courtlistener.com/api/rest/v4/opinions/} for federal
 * and state court opinions mentioning AI, machine learning, medical devices,
 * or healthcare algorithms. Results are mapped to {@link NewsArticle}
 * records with topic "AI Healthcare Legal" so they flow into the
 * Litigation category on the Legal Timeline page.
 *
 * <p>The API requires a free auth token (set {@code COURTLISTENER_API_TOKEN}
 * env var). When the token is absent, the harvester gracefully returns
 * an empty list. Pagination is supported via the {@code next} URL in
 * the response.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@Slf4j
@Component
public class CourtListenerHarvester {

    private static final String BASE_URL = "https://www.courtlistener.com/api/rest/v4/opinions/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 50;
    private static final String TOPIC = "AI Healthcare Legal";
    private static final String SOURCE_NAME = "CourtListener";

    private static final String SEARCH_QUERY =
            "\"artificial intelligence\" healthcare OR \"machine learning\" \"medical device\" "
            + "OR \"AI\" FDA OR algorithm clinical";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiToken;

    public CourtListenerHarvester(
            @Value("${COURTLISTENER_API_TOKEN:}") String apiToken) {
        log.debug("CourtListenerHarvester() | apiToken={}", apiToken.isEmpty() ? "absent" : "present");
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Returns a short label for this source (used in logging).
     */
    public String sourceName() {
        log.debug("sourceName()");
        String result = SOURCE_NAME;
        log.debug("sourceName() | return={}", result);
        return result;
    }

    /**
     * Harvests court opinions from CourtListener within the lookback window.
     *
     * @param lookbackDays how many days back to search
     * @return list of articles mapped from court opinions
     */
    public List<NewsArticle> harvest(int lookbackDays) {
        log.debug("harvest() | lookbackDays={}", lookbackDays);

        List<NewsArticle> articles = new ArrayList<>();

        if (apiToken == null || apiToken.isEmpty()) {
            log.info("harvest() | CourtListener API token not configured, skipping");
            log.debug("harvest() | return=0 articles");
            return articles;
        }

        try {
            String fromDate = LocalDate.now(ZoneOffset.UTC).minusDays(lookbackDays).toString();
            String encodedQuery = URLEncoder.encode(SEARCH_QUERY, StandardCharsets.UTF_8);

            String nextUrl = BASE_URL
                    + "?q=" + encodedQuery
                    + "&date_filed__gte=" + fromDate
                    + "&format=json"
                    + "&page_size=" + PAGE_SIZE;

            int pageCount = 0;
            while (nextUrl != null && pageCount < MAX_PAGES) {
                pageCount++;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(nextUrl))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Accept", "application/json")
                        .header("Authorization", "Token " + apiToken)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.debug("harvest() | CourtListener API returned status={}, page={}", response.statusCode(), pageCount);

                if (response.statusCode() != 200) {
                    log.warn("harvest() | CourtListener API returned non-200: {}", response.statusCode());
                    break;
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode results = root.get("results");
                if (results == null || !results.isArray() || results.isEmpty()) {
                    log.info("harvest() | no results in CourtListener response at page={}", pageCount);
                    break;
                }

                for (JsonNode node : results) {
                    NewsArticle article = mapToArticle(node);
                    if (article != null) {
                        articles.add(article);
                    }
                }

                // Follow pagination
                JsonNode nextNode = root.get("next");
                if (nextNode != null && !nextNode.isNull()) {
                    nextUrl = nextNode.asText();
                } else {
                    nextUrl = null;
                }
            }

        } catch (Exception e) {
            log.warn("harvest() | CourtListener harvest failed: {}", e.getMessage());
        }

        log.info("harvest() | harvested {} court opinions from CourtListener", articles.size());
        log.debug("harvest() | return={} articles", articles.size());
        return articles;
    }

    private NewsArticle mapToArticle(JsonNode node) {
        String id = textOrNull(node, "id");
        String caseName = textOrNull(node, "case_name");
        String absoluteUrl = textOrNull(node, "absolute_url");
        String snippet = textOrNull(node, "snippet");
        String dateFiled = textOrNull(node, "date_filed");

        if (caseName == null || id == null) {
            return null;
        }

        String articleId = "courtlistener-" + id;
        String urlStr = absoluteUrl != null
                ? "https://www.courtlistener.com" + absoluteUrl
                : "https://www.courtlistener.com/opinion/" + id + "/";

        URI articleUrl;
        try {
            articleUrl = URI.create(urlStr);
        } catch (Exception e) {
            log.debug("mapToArticle() | invalid URL for opinion {}: {}", id, urlStr);
            return null;
        }

        // Extract court name from nested object if available
        String courtName = null;
        JsonNode courtNode = node.get("court");
        if (courtNode != null && !courtNode.isNull()) {
            courtName = textOrNull(courtNode, "short_name");
            if (courtName == null) {
                courtName = textOrNull(courtNode, "full_name");
            }
        }

        // Clean HTML from snippet
        String bodyText = snippet != null ? snippet.replaceAll("<[^>]+>", "").trim() : null;

        Instant publishedAt = parseDate(dateFiled);

        return new NewsArticle(
                articleId,
                caseName,
                articleUrl,
                bodyText,
                TOPIC,
                courtName,
                null,
                SOURCE_NAME,
                "ACADEMIC",
                0.85,
                publishedAt
        );
    }

    private Instant parseDate(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return date.atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.asText();
        return text.isEmpty() ? null : text;
    }
}
