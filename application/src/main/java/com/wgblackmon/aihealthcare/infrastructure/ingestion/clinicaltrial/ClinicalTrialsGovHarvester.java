package com.wgblackmon.aihealthcare.infrastructure.ingestion.clinicaltrial;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialPhase;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialHarvestingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Harvests clinical trials from the ClinicalTrials.gov v2 API.
 *
 * <p>Queries {@code clinicaltrials.gov/api/v2/studies} for recently posted
 * studies matching configured AI/healthcare keywords. Each result is mapped
 * to a {@link ClinicalTrial} domain record.
 *
 * <p>The API is free and requires no API key. Rate limits are generous
 * for the volume we query (~100 results per keyword batch).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
@Slf4j
@Component
public class ClinicalTrialsGovHarvester implements ClinicalTrialHarvestingPort {

    private static final String BASE_URL = "https://clinicaltrials.gov/api/v2/studies";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ClinicalTrialHarvestProperties properties;

    public ClinicalTrialsGovHarvester(ClinicalTrialHarvestProperties properties) {
        log.debug("ClinicalTrialsGovHarvester() | properties={}", properties.getClass().getSimpleName());
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public List<ClinicalTrial> harvestAll() {
        log.debug("harvestAll()");

        if (!properties.isEnabled()) {
            log.info("harvestAll() | clinical trial harvesting is disabled");
            log.debug("harvestAll() | return=0 trials");
            return List.of();
        }

        List<ClinicalTrial> allTrials = new ArrayList<>();
        List<String> aiKeywords = properties.getAiKeywords();

        // Build a combined query term from all keywords
        StringBuilder queryBuilder = new StringBuilder();
        for (int i = 0; i < aiKeywords.size(); i++) {
            if (i > 0) {
                queryBuilder.append(" OR ");
            }
            queryBuilder.append("\"").append(aiKeywords.get(i)).append("\"");
        }
        String queryTerm = queryBuilder.toString();

        try {
            String fromDate = LocalDate.now(ZoneOffset.UTC)
                    .minusDays(properties.getLookbackDays()).format(ISO_DATE);
            String toDate = LocalDate.now(ZoneOffset.UTC).format(ISO_DATE);

            String filterAdvanced = "AREA[StudyFirstPostDate]RANGE["
                    + fromDate + "," + toDate + "]";

            String url = BASE_URL
                    + "?query.term=" + URLEncoder.encode(queryTerm, StandardCharsets.UTF_8)
                    + "&filter.advanced=" + URLEncoder.encode(filterAdvanced, StandardCharsets.UTF_8)
                    + "&pageSize=" + properties.getMaxResults()
                    + "&format=json";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("harvestAll() | ClinicalTrials.gov API returned status={}", response.statusCode());

            if (response.statusCode() != 200) {
                log.warn("harvestAll() | ClinicalTrials.gov API returned non-200: {}", response.statusCode());
                log.debug("harvestAll() | return=0 trials");
                return allTrials;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode studies = root.get("studies");
            if (studies == null || !studies.isArray()) {
                log.info("harvestAll() | no studies in ClinicalTrials.gov response");
                log.debug("harvestAll() | return=0 trials");
                return allTrials;
            }

            for (JsonNode study : studies) {
                ClinicalTrial trial = parseStudy(study, aiKeywords);
                if (trial != null) {
                    allTrials.add(trial);
                }
            }

        } catch (Exception e) {
            log.warn("harvestAll() | ClinicalTrials.gov harvest failed: {}", e.getMessage());
        }

        log.info("harvestAll() | harvested {} trials from ClinicalTrials.gov", allTrials.size());
        log.debug("harvestAll() | return={} trials", allTrials.size());
        return allTrials;
    }

    private ClinicalTrial parseStudy(JsonNode study, List<String> aiKeywords) {
        JsonNode protocolSection = study.get("protocolSection");
        if (protocolSection == null) {
            return null;
        }

        // Identification
        JsonNode idModule = protocolSection.get("identificationModule");
        if (idModule == null) {
            return null;
        }
        String nctId = textOrNull(idModule, "nctId");
        if (nctId == null) {
            return null;
        }
        String briefTitle = textOrNull(idModule, "briefTitle");
        String officialTitle = textOrNull(idModule, "officialTitle");
        String title = briefTitle != null ? briefTitle : officialTitle;
        if (title == null || title.isBlank()) {
            return null;
        }

        // Organization (sponsor)
        String sponsor = null;
        JsonNode orgModule = protocolSection.get("sponsorCollaboratorsModule");
        if (orgModule != null) {
            JsonNode leadSponsor = orgModule.get("leadSponsor");
            if (leadSponsor != null) {
                sponsor = textOrNull(leadSponsor, "name");
            }
        }

        // Status
        ClinicalTrialStatus status = ClinicalTrialStatus.UNKNOWN;
        JsonNode statusModule = protocolSection.get("statusModule");
        if (statusModule != null) {
            String overallStatus = textOrNull(statusModule, "overallStatus");
            status = parseStatus(overallStatus);
        }

        // Phase
        ClinicalTrialPhase phase = null;
        JsonNode designModule = protocolSection.get("designModule");
        if (designModule != null) {
            JsonNode phases = designModule.get("phases");
            if (phases != null && phases.isArray() && phases.size() > 0) {
                phase = parsePhase(phases.get(0).asText());
            }
            // studyType
        }
        String studyType = null;
        if (designModule != null) {
            studyType = textOrNull(designModule, "studyType");
        }

        // Conditions
        List<String> conditions = new ArrayList<>();
        JsonNode conditionsModule = protocolSection.get("conditionsModule");
        if (conditionsModule != null) {
            JsonNode conditionsArray = conditionsModule.get("conditions");
            if (conditionsArray != null && conditionsArray.isArray()) {
                for (JsonNode c : conditionsArray) {
                    conditions.add(c.asText());
                }
            }
        }

        // Brief summary
        String briefSummary = null;
        JsonNode descModule = protocolSection.get("descriptionModule");
        if (descModule != null) {
            briefSummary = textOrNull(descModule, "briefSummary");
        }

        // Start date
        Instant startDate = null;
        if (statusModule != null) {
            JsonNode startDateStruct = statusModule.get("startDateStruct");
            if (startDateStruct != null) {
                startDate = parseDateStruct(startDateStruct);
            }
        }

        // Determine matched AI keywords
        String searchText = (title + " " + (briefSummary != null ? briefSummary : "")
                + " " + (sponsor != null ? sponsor : "")).toLowerCase();
        List<String> matchedKeywords = matchKeywords(searchText, aiKeywords);

        String sourceUrl = "https://clinicaltrials.gov/study/" + nctId;

        return new ClinicalTrial(
                UUID.randomUUID().toString(),
                nctId,
                title,
                sponsor,
                status,
                phase,
                conditions,
                briefSummary,
                sourceUrl,
                studyType,
                startDate,
                Instant.now(),
                matchedKeywords
        );
    }

    private ClinicalTrialStatus parseStatus(String status) {
        if (status == null) {
            return ClinicalTrialStatus.UNKNOWN;
        }
        String normalized = status.toUpperCase().replace(" ", "_").replace("-", "_");
        try {
            return ClinicalTrialStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.debug("parseStatus() | unknown status '{}', defaulting to UNKNOWN", status);
            return ClinicalTrialStatus.UNKNOWN;
        }
    }

    private ClinicalTrialPhase parsePhase(String phase) {
        if (phase == null) {
            return null;
        }
        String normalized = phase.toUpperCase().replace(" ", "_").replace("-", "_");
        // ClinicalTrials.gov uses "PHASE1", "PHASE2", etc. — map to our enum
        normalized = normalized.replace("PHASE1", "PHASE_1")
                .replace("PHASE2", "PHASE_2")
                .replace("PHASE3", "PHASE_3")
                .replace("PHASE4", "PHASE_4");
        if (normalized.equals("EARLY_PHASE1")) {
            normalized = "EARLY_PHASE_1";
        }
        if (normalized.equals("NA") || normalized.equals("N/A") || normalized.equals("NOT_APPLICABLE")) {
            return ClinicalTrialPhase.NOT_APPLICABLE;
        }
        try {
            return ClinicalTrialPhase.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.debug("parsePhase() | unknown phase '{}', defaulting to NOT_APPLICABLE", phase);
            return ClinicalTrialPhase.NOT_APPLICABLE;
        }
    }

    private Instant parseDateStruct(JsonNode dateStruct) {
        String date = textOrNull(dateStruct, "date");
        if (date == null) {
            return null;
        }
        try {
            // ClinicalTrials.gov dates can be "YYYY-MM-DD" or "YYYY-MM" or "YYYY"
            if (date.length() == 10) {
                LocalDate ld = LocalDate.parse(date, ISO_DATE);
                return ld.atStartOfDay(ZoneOffset.UTC).toInstant();
            } else if (date.length() == 7) {
                LocalDate ld = LocalDate.parse(date + "-01", ISO_DATE);
                return ld.atStartOfDay(ZoneOffset.UTC).toInstant();
            } else if (date.length() == 4) {
                LocalDate ld = LocalDate.parse(date + "-01-01", ISO_DATE);
                return ld.atStartOfDay(ZoneOffset.UTC).toInstant();
            }
        } catch (Exception e) {
            log.debug("parseDateStruct() | failed to parse date '{}'", date);
        }
        return null;
    }

    private List<String> matchKeywords(String text, List<String> aiKeywords) {
        List<String> matched = new ArrayList<>();
        for (String keyword : aiKeywords) {
            if (text.contains(keyword.toLowerCase())) {
                matched.add(keyword);
            }
        }
        return matched;
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
