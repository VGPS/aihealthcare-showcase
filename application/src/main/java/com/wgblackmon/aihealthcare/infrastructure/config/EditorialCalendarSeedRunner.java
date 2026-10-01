package com.wgblackmon.aihealthcare.infrastructure.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wgblackmon.aihealthcare.domain.model.EditorialDemandSignal;
import com.wgblackmon.aihealthcare.domain.model.EditorialEffort;
import com.wgblackmon.aihealthcare.domain.model.EditorialItem;
import com.wgblackmon.aihealthcare.domain.model.EditorialPriority;
import com.wgblackmon.aihealthcare.domain.model.EditorialSource;
import com.wgblackmon.aihealthcare.domain.model.EditorialStatus;
import com.wgblackmon.aihealthcare.domain.model.EditorialTheme;
import com.wgblackmon.aihealthcare.domain.port.outbound.EditorialCalendarPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Seeds the healthcare AI editorial calendar on application startup from a
 * JSON dataset bundled in the classpath.
 *
 * <p>Reads {@code data/editorial_calendar_seed.json}, maps each JSON object
 * to an {@link EditorialItem} domain record, and calls
 * {@link EditorialCalendarPort#upsert(EditorialItem)} for each. Idempotent —
 * re-running on restart re-upserts all 13 items without creating duplicates.
 *
 * <p>Runs at {@link Order}(2) — after {@code StateLawSeedRunner} which owns
 * {@code @Order(1)}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@Slf4j
@Component
@Order(2)
public class EditorialCalendarSeedRunner implements ApplicationRunner {

    private static final String SEED_FILE = "data/editorial_calendar_seed.json";
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE =
            new TypeReference<>() {};

    private final EditorialCalendarPort editorialCalendarPort;

    public EditorialCalendarSeedRunner(EditorialCalendarPort editorialCalendarPort) {
        log.debug("EditorialCalendarSeedRunner() | editorialCalendarPort={}",
                editorialCalendarPort.getClass().getSimpleName());
        this.editorialCalendarPort = editorialCalendarPort;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.debug("run() | seeding editorial calendar from {}", SEED_FILE);
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SEED_FILE)) {
            if (is == null) {
                log.warn("run() | seed file not found on classpath: {}", SEED_FILE);
                return;
            }
            List<Map<String, Object>> rows = mapper.readValue(is, LIST_TYPE);
            int seeded = 0;
            for (Map<String, Object> row : rows) {
                try {
                    EditorialItem item = mapToItem(row);
                    editorialCalendarPort.upsert(item);
                    seeded++;
                } catch (Exception e) {
                    log.warn("run() | skipping malformed row id={}: {}", row.get("id"), e.getMessage());
                }
            }
            log.info("run() | editorial calendar seeded: {} items", seeded);
        } catch (Exception e) {
            log.error("run() | failed to seed editorial calendar: {}", e.getMessage(), e);
        }
        log.debug("run() | return=void");
    }

    @SuppressWarnings("unchecked")
    private EditorialItem mapToItem(Map<String, Object> row) {
        String id = (String) row.get("id");
        String title = (String) row.get("title");
        String hook = (String) row.get("hook");
        EditorialTheme theme = EditorialTheme.valueOf((String) row.get("theme"));
        EditorialDemandSignal demandSignal = row.get("demandSignal") != null
                ? EditorialDemandSignal.valueOf((String) row.get("demandSignal")) : null;
        EditorialPriority priorityTier = EditorialPriority.valueOf((String) row.get("priorityTier"));
        EditorialEffort effort = row.get("effort") != null
                ? EditorialEffort.valueOf((String) row.get("effort")) : null;
        String format = (String) row.get("format");
        LocalDate publishWindowStart = parseDate(row.get("publishWindowStart"));
        LocalDate publishWindowEnd = parseDate(row.get("publishWindowEnd"));
        LocalDate preferredDate = parseDate(row.get("preferredDate"));
        String cta = (String) row.get("cta");
        EditorialStatus status = row.get("status") != null
                ? EditorialStatus.valueOf((String) row.get("status")) : EditorialStatus.PLANNED;
        LocalDate lastVerified = parseDate(row.get("lastVerified"));

        List<String> audiences = row.get("audiences") instanceof List<?> rawList
                ? rawList.stream().map(Object::toString).toList()
                : List.of();

        List<EditorialSource> sources = List.of();
        if (row.get("primarySources") instanceof List<?> rawSources) {
            sources = rawSources.stream()
                    .filter(s -> s instanceof Map)
                    .map(s -> (Map<String, Object>) s)
                    .map(s -> new EditorialSource(
                            (String) s.get("url"),
                            (String) s.get("label"),
                            (String) s.get("sourceType")))
                    .toList();
        }

        return new EditorialItem(id, title, hook, theme, demandSignal, priorityTier, effort,
                format, publishWindowStart, publishWindowEnd, preferredDate, cta,
                status, lastVerified, audiences, sources);
    }

    private LocalDate parseDate(Object value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
