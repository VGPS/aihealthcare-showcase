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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Harvests FDA 510(k) clearances from the openFDA device API.
 *
 * <p>Queries {@code api.fda.gov/device/510k.json} for recently cleared
 * devices and filters for AI/ML-relevant devices using configurable
 * keywords matched against {@code device_name} and
 * {@code advisory_committee_description}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-30
 */
@Slf4j
@Component
public class Fda510kHarvester implements RegulatorySourceHarvester {

    private static final String BASE_URL = "https://api.fda.gov/device/510k.json";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RESULTS = 100;
    private static final int MAX_SKIP = 5000;
    private static final DateTimeFormatter FDA_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public Fda510kHarvester() {
        log.debug("Fda510kHarvester()");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String sourceName() {
        return "FDA 510(k)";
    }

    @Override
    public List<RegulatoryEvent> harvest(int lookbackDays, List<String> aiKeywords) {
        log.debug("harvest() | lookbackDays={}, aiKeywords={}", lookbackDays, aiKeywords.size());

        List<RegulatoryEvent> events = new ArrayList<>();
        try {
            String fromDate = LocalDate.now(ZoneOffset.UTC).minusDays(lookbackDays).format(FDA_DATE);
            String toDate = LocalDate.now(ZoneOffset.UTC).format(FDA_DATE);
            String searchParam = "decision_date:[" + fromDate + "+TO+" + toDate + "]";

            int skip = 0;
            boolean hasMore = true;
            while (hasMore) {
                String url = BASE_URL + "?search=" + searchParam
                        + "&limit=" + MAX_RESULTS + "&skip=" + skip;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.debug("harvest() | FDA 510(k) API returned status={}, skip={}", response.statusCode(), skip);

                if (response.statusCode() != 200) {
                    log.warn("harvest() | FDA 510(k) API returned non-200: {}", response.statusCode());
                    break;
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode results = root.get("results");
                if (results == null || !results.isArray() || results.isEmpty()) {
                    log.info("harvest() | no results in FDA 510(k) response at skip={}", skip);
                    break;
                }

                for (JsonNode node : results) {
                    String kNumber = textOrNull(node, "k_number");
                    String deviceName = textOrNull(node, "device_name");
                    String applicant = textOrNull(node, "applicant");
                    String advisoryCommittee = textOrNull(node, "advisory_committee_description");
                    String decisionDate = textOrNull(node, "decision_date");
                    String productCode = textOrNull(node, "product_code");

                    // Check AI-relevance
                    String searchText = (deviceName != null ? deviceName : "")
                            + " " + (advisoryCommittee != null ? advisoryCommittee : "")
                            + " " + (productCode != null ? productCode : "");
                    List<String> matchedKeywords = matchKeywords(searchText, aiKeywords);
                    if (matchedKeywords.isEmpty()) {
                        continue;
                    }

                    Instant publishedAt = parseDecisionDate(decisionDate);
                    String sourceUrl = "https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfpmn/pmn.cfm?ID=" + kNumber;

                    RegulatoryEvent event = new RegulatoryEvent(
                            UUID.randomUUID().toString(),
                            RegulatoryEventType.FDA_510K_CLEARANCE,
                            RegulatoryBody.FDA,
                            "510(k) Clearance: " + (deviceName != null ? deviceName : kNumber),
                            buildSummary(applicant, deviceName, advisoryCommittee),
                            kNumber,
                            applicant,
                            deviceName,
                            sourceUrl,
                            null,
                            publishedAt,
                            Instant.now(),
                            matchedKeywords
                    );
                    events.add(event);
                }

                // Paginate if we got a full page of results
                if (results.size() < MAX_RESULTS) {
                    hasMore = false;
                } else {
                    skip += MAX_RESULTS;
                    if (skip > MAX_SKIP) {
                        log.info("harvest() | reached pagination safety cap at skip={}", skip);
                        hasMore = false;
                    }
                }
            }

        } catch (Exception e) {
            log.warn("harvest() | FDA 510(k) harvest failed: {}", e.getMessage());
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

    private Instant parseDecisionDate(String dateStr) {
        if (dateStr == null || dateStr.length() < 8) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateStr.substring(0, 8), FDA_DATE);
            return date.atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private String buildSummary(String applicant, String deviceName, String committee) {
        StringBuilder sb = new StringBuilder();
        if (applicant != null) {
            sb.append(applicant).append(" — ");
        }
        if (deviceName != null) {
            sb.append(deviceName);
        }
        if (committee != null) {
            sb.append(" (").append(committee).append(")");
        }
        return sb.toString();
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
