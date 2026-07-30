package com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Harvests CMS proposed and final rules from the Federal Register API.
 *
 * <p>Queries {@code federalregister.gov/api/v1/documents.json} filtered
 * by the CMS agency and AI/healthcare-related keywords.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-30
 */
@Slf4j
@Component
public class CmsRuleHarvester implements RegulatorySourceHarvester {

    private static final String BASE_URL = "https://www.federalregister.gov/api/v1/documents.json";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int PER_PAGE = 50;
    private static final int MAX_PAGES = 50;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CmsRuleHarvester() {
        log.debug("CmsRuleHarvester()");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String sourceName() {
        return "CMS Federal Register";
    }

    @Override
    public List<RegulatoryEvent> harvest(int lookbackDays, List<String> aiKeywords) {
        log.debug("harvest() | lookbackDays={}, aiKeywords={}", lookbackDays, aiKeywords.size());

        List<RegulatoryEvent> events = new ArrayList<>();
        try {
            String fromDate = LocalDate.now(ZoneOffset.UTC).minusDays(lookbackDays).toString();

            int page = 1;
            boolean hasMore = true;
            while (hasMore) {
                String url = BASE_URL
                        + "?conditions[agencies][]=centers-for-medicare-medicaid-services"
                        + "&conditions[type][]=RULE"
                        + "&conditions[type][]=PRORULE"
                        + "&conditions[publication_date][gte]=" + fromDate
                        + "&per_page=" + PER_PAGE
                        + "&page=" + page
                        + "&order=newest";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.debug("harvest() | Federal Register API returned status={}, page={}", response.statusCode(), page);

                if (response.statusCode() != 200) {
                    log.warn("harvest() | Federal Register API returned non-200: {}", response.statusCode());
                    break;
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode results = root.get("results");
                if (results == null || !results.isArray() || results.isEmpty()) {
                    log.info("harvest() | no results in Federal Register response at page={}", page);
                    break;
                }

                for (JsonNode node : results) {
                    String title = textOrNull(node, "title");
                    String docType = textOrNull(node, "type");
                    String abstractText = textOrNull(node, "abstract");
                    String htmlUrl = textOrNull(node, "html_url");
                    String pubDate = textOrNull(node, "publication_date");
                    String docNumber = textOrNull(node, "document_number");

                    if (title == null || htmlUrl == null) {
                        continue;
                    }

                    // Check AI-relevance in title + abstract
                    String searchText = title + " " + (abstractText != null ? abstractText : "");
                    List<String> matchedKeywords = matchKeywords(searchText, aiKeywords);
                    if (matchedKeywords.isEmpty()) {
                        continue;
                    }

                    RegulatoryEventType eventType = "RULE".equalsIgnoreCase(docType)
                            ? RegulatoryEventType.CMS_FINAL_RULE
                            : RegulatoryEventType.CMS_PROPOSED_RULE;

                    Instant publishedAt = parseDate(pubDate);

                    RegulatoryEvent event = new RegulatoryEvent(
                            UUID.randomUUID().toString(),
                            eventType,
                            RegulatoryBody.CMS,
                            title,
                            abstractText != null && abstractText.length() > 500
                                    ? abstractText.substring(0, 500) : abstractText,
                            docNumber,
                            null,
                            null,
                            htmlUrl,
                            null,
                            publishedAt,
                            Instant.now(),
                            matchedKeywords
                    );
                    events.add(event);
                }

                // Check for next page
                JsonNode nextPageUrl = root.get("next_page_url");
                if (nextPageUrl == null || nextPageUrl.isNull()) {
                    hasMore = false;
                } else {
                    page++;
                    if (page > MAX_PAGES) {
                        log.info("harvest() | reached pagination safety cap at page={}", page);
                        hasMore = false;
                    }
                }
            }

        } catch (Exception e) {
            log.warn("harvest() | CMS rule harvest failed: {}", e.getMessage());
        }

        log.debug("harvest() | return={} events", events.size());
        return events;
    }

    private List<String> matchKeywords(String text, List<String> aiKeywords) {
        String lower = text.toLowerCase();
        List<String> matched = new ArrayList<>();
        for (String keyword : aiKeywords) {
            if (lower.contains(keyword.toLowerCase())) {
                matched.add(keyword);
            }
        }
        return matched;
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
