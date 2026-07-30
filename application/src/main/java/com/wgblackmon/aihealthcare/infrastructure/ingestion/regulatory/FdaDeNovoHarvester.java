package com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryOutcomeStatus;
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
 * Harvests FDA De Novo classification decisions from the openFDA API.
 *
 * <p>De Novo classifications are particularly important for novel AI/ML
 * medical devices that lack a predicate device for the traditional
 * 510(k) pathway.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-30
 */
@Slf4j
@Component
public class FdaDeNovoHarvester implements RegulatorySourceHarvester {

    private static final String BASE_URL = "https://api.fda.gov/device/classification.json";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RESULTS = 100;
    private static final DateTimeFormatter FDA_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FdaDeNovoHarvester() {
        log.debug("FdaDeNovoHarvester()");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String sourceName() {
        return "FDA De Novo";
    }

    @Override
    public List<RegulatoryEvent> harvest(int lookbackDays, List<String> aiKeywords) {
        log.debug("harvest() | lookbackDays={}, aiKeywords={}", lookbackDays, aiKeywords.size());

        List<RegulatoryEvent> events = new ArrayList<>();
        try {
            String fromDate = LocalDate.now(ZoneOffset.UTC).minusDays(lookbackDays).format(FDA_DATE);
            String toDate = LocalDate.now(ZoneOffset.UTC).format(FDA_DATE);

            String url = BASE_URL + "?search=date_premarket_decision:[" + fromDate + "+TO+" + toDate + "]"
                    + "+AND+submission_type_id:4"
                    + "&limit=" + MAX_RESULTS;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("harvest() | FDA De Novo API returned status={}", response.statusCode());

            if (response.statusCode() != 200) {
                log.warn("harvest() | FDA De Novo API returned non-200: {}", response.statusCode());
                return events;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.get("results");
            if (results == null || !results.isArray()) {
                log.info("harvest() | no results in FDA De Novo response");
                return events;
            }

            for (JsonNode node : results) {
                String deNovoNumber = textOrNull(node, "de_novo_number");
                if (deNovoNumber == null) {
                    deNovoNumber = textOrNull(node, "regulation_number");
                }
                String deviceName = textOrNull(node, "device_name");
                String deviceClass = textOrNull(node, "device_class");
                String medSpecialty = textOrNull(node, "medical_specialty_description");
                String decisionDate = textOrNull(node, "date_premarket_decision");

                // Check AI-relevance
                String searchText = (deviceName != null ? deviceName : "")
                        + " " + (medSpecialty != null ? medSpecialty : "");
                List<String> matchedKeywords = matchKeywords(searchText, aiKeywords);
                if (matchedKeywords.isEmpty()) {
                    continue;
                }

                Instant publishedAt = parseDate(decisionDate);
                String sourceUrl = "https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfpmn/denovo.cfm?id=" + deNovoNumber;

                RegulatoryEvent event = new RegulatoryEvent(
                        UUID.randomUUID().toString(),
                        RegulatoryEventType.FDA_DE_NOVO_CLASSIFICATION,
                        RegulatoryBody.FDA,
                        "De Novo Classification: " + (deviceName != null ? deviceName : deNovoNumber),
                        buildSummary(deviceName, deviceClass, medSpecialty),
                        deNovoNumber,
                        null,
                        deviceName,
                        sourceUrl,
                        null,
                        publishedAt,
                        Instant.now(),
                        matchedKeywords,
                        RegulatoryOutcomeStatus.APPROVED,
                        Instant.now(),
                        null,
                        null
                );
                events.add(event);
            }

        } catch (Exception e) {
            log.warn("harvest() | FDA De Novo harvest failed: {}", e.getMessage());
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

    private String buildSummary(String deviceName, String deviceClass, String medSpecialty) {
        StringBuilder sb = new StringBuilder();
        if (deviceName != null) {
            sb.append(deviceName);
        }
        if (deviceClass != null) {
            sb.append(" — Class ").append(deviceClass);
        }
        if (medSpecialty != null) {
            sb.append(" (").append(medSpecialty).append(")");
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
