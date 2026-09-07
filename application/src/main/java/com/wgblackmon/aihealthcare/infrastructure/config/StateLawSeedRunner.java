package com.wgblackmon.aihealthcare.infrastructure.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.LawSource;
import com.wgblackmon.aihealthcare.domain.model.LawStatus;
import com.wgblackmon.aihealthcare.domain.model.SourceType;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;
import com.wgblackmon.aihealthcare.domain.port.outbound.StateLawPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Seeds the state health-AI legislation registry on application startup
 * from a JSON dataset bundled in the classpath.
 *
 * <p>Reads {@code data/state_health_ai_laws_seed.json}, maps each JSON
 * object to a {@link StateLaw} domain record, and calls
 * {@link StateLawPort#upsert(StateLaw)} for each. Idempotent — existing
 * records are updated if the dataset version is newer.
 *
 * <p>Runs at {@code @Order(1)} so it executes before any other
 * {@link ApplicationRunner} beans that might depend on seeded data.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
@Slf4j
@Component
@Order(1)
public class StateLawSeedRunner implements ApplicationRunner {

    private static final String SEED_FILE = "data/state_health_ai_laws_seed.json";

    private final StateLawPort stateLawPort;

    public StateLawSeedRunner(StateLawPort stateLawPort) {
        log.debug("StateLawSeedRunner() | stateLawPort={}", stateLawPort.getClass().getSimpleName());
        this.stateLawPort = stateLawPort;
    }

    /**
     * Loads and upserts the seed dataset on startup.
     *
     * @param args application arguments (unused)
     */
    @Override
    public void run(ApplicationArguments args) {
        log.debug("run() | loading seed file={}", SEED_FILE);

        InputStream is = getClass().getClassLoader().getResourceAsStream(SEED_FILE);
        if (is == null) {
            log.warn("run() | seed file not found on classpath: {}", SEED_FILE);
            log.debug("run() | return=void (no seed file)");
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            List<Map<String, Object>> rawList = mapper.readValue(is,
                    new TypeReference<List<Map<String, Object>>>() {});

            Instant now = Instant.now();
            int count = 0;

            for (Map<String, Object> raw : rawList) {
                try {
                    StateLaw law = mapToStateLaw(raw, now);
                    stateLawPort.upsert(law);
                    count++;
                } catch (Exception e) {
                    log.warn("run() | skipping seed record id={}: {}",
                             raw.get("id"), e.getMessage());
                }
            }

            log.info("StateLawSeedRunner | seeded {} laws (state + federal)", count);
            log.debug("run() | return=void");
        } catch (Exception e) {
            log.error("run() | failed to load seed data: {}", e.getMessage(), e);
        }
    }

    /**
     * Maps a raw JSON object to a {@link StateLaw} domain record.
     *
     * @param raw the JSON object as a map
     * @param now the current timestamp for createdAt/updatedAt
     * @return the mapped StateLaw record
     */
    @SuppressWarnings("unchecked")
    private StateLaw mapToStateLaw(Map<String, Object> raw, Instant now) {
        String id = (String) raw.get("id");
        StateCode stateCode = StateCode.valueOf((String) raw.get("stateCode"));
        String stateName = (String) raw.get("stateName");
        String billNumber = (String) raw.get("billNumber");
        String title = (String) raw.get("title");
        int yearEnacted = raw.get("yearEnacted") != null
                ? ((Number) raw.get("yearEnacted")).intValue() : 0;
        String dateSigned = (String) raw.get("dateSigned");
        String dateSignedNote = (String) raw.get("dateSignedNote");
        String effectiveDate = (String) raw.get("effectiveDate");
        String effectiveDateNote = (String) raw.get("effectiveDateNote");
        LawStatus status = LawStatus.valueOf((String) raw.get("status"));
        String statusDetail = (String) raw.get("statusDetail");

        // Parse categories
        List<LawCategory> categories = new ArrayList<>();
        List<String> rawCategories = (List<String>) raw.get("categories");
        if (rawCategories != null) {
            for (String cat : rawCategories) {
                categories.add(LawCategory.valueOf(cat));
            }
        }

        String regulatedParties = (String) raw.get("regulatedParties");
        String keyRequirements = (String) raw.get("keyRequirements");
        String enforcement = (String) raw.get("enforcement");

        // Parse sources
        List<LawSource> sources = new ArrayList<>();
        List<Map<String, Object>> rawSources = (List<Map<String, Object>>) raw.get("sources");
        if (rawSources != null) {
            for (Map<String, Object> src : rawSources) {
                SourceType sourceType = SourceType.valueOf((String) src.get("type"));
                String url = (String) src.get("url");
                sources.add(new LawSource(sourceType, url, null, null, null, false));
            }
        }

        String notes = (String) raw.get("notes");
        String datasetVersion = (String) raw.get("datasetVersion");

        return new StateLaw(
                id, stateCode, stateName, billNumber, title,
                yearEnacted, dateSigned, dateSignedNote,
                effectiveDate, effectiveDateNote,
                status, statusDetail, categories,
                regulatedParties, keyRequirements, enforcement,
                sources, notes, datasetVersion,
                now, now
        );
    }
}
