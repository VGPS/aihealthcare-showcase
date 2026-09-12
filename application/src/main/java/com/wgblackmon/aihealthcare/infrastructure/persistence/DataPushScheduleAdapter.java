package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.service.PipeDelimitedUtils;
import com.wgblackmon.aihealthcare.domain.model.DataPushSchedule;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataPushSchedulePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link DataPushSchedulePort}.
 *
 * <p>Maps between the {@link DataPushSchedule} domain record and
 * {@link EnterprisePushScheduleEntity}. Recipients are stored as
 * pipe-delimited text; parameters as JSON; enums as their
 * {@code name()} strings.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-12
 */
@Slf4j
@Component
public class DataPushScheduleAdapter implements DataPushSchedulePort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final EnterprisePushScheduleRepository repository;

    public DataPushScheduleAdapter(EnterprisePushScheduleRepository repository) {
        this.repository = repository;
        log.debug("DataPushScheduleAdapter() | repository={}", repository.getClass().getSimpleName());
    }

    @Override
    @Transactional
    public DataPushSchedule save(DataPushSchedule schedule) {
        log.debug("save() | scheduleId={}", schedule.scheduleId());
        EnterprisePushScheduleEntity entity = toEntity(schedule);
        EnterprisePushScheduleEntity saved = repository.save(entity);
        DataPushSchedule result = toDomain(saved);
        log.debug("save() | return={}", result.scheduleId());
        return result;
    }

    @Override
    public Optional<DataPushSchedule> findByScheduleIdAndOwnerEmail(String scheduleId, String ownerEmail) {
        log.debug("findByScheduleIdAndOwnerEmail() | scheduleId={}, ownerEmail={}", scheduleId, ownerEmail);
        Optional<DataPushSchedule> result = repository.findByScheduleIdAndOwnerEmail(scheduleId, ownerEmail)
                .map(this::toDomain);
        log.debug("findByScheduleIdAndOwnerEmail() | return={}", result.isPresent());
        return result;
    }

    @Override
    public List<DataPushSchedule> findByOwnerEmail(String ownerEmail) {
        log.debug("findByOwnerEmail() | ownerEmail={}", ownerEmail);
        List<DataPushSchedule> result = repository.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail)
                .stream().map(this::toDomain).toList();
        log.debug("findByOwnerEmail() | return={} schedules", result.size());
        return result;
    }

    @Override
    public List<DataPushSchedule> findDue(Instant now, int limit) {
        log.debug("findDue() | now={}, limit={}", now, limit);
        List<DataPushSchedule> result = repository.findDue(now, PageRequest.of(0, limit))
                .stream().map(this::toDomain).toList();
        log.debug("findDue() | return={} schedules", result.size());
        return result;
    }

    @Override
    @Transactional
    public boolean claim(String scheduleId, Instant observedNextRunAt, Instant newNextRunAt, Instant now) {
        log.debug("claim() | scheduleId={}, observedNextRunAt={}", scheduleId, observedNextRunAt);
        int rowsUpdated = repository.claim(scheduleId, observedNextRunAt, newNextRunAt, now);
        boolean result = rowsUpdated == 1;
        log.debug("claim() | return={}", result);
        return result;
    }

    @Override
    @Transactional
    public void recordOutcome(String scheduleId, String jobId, DataJobStatus status, Instant completedAt) {
        log.debug("recordOutcome() | scheduleId={}, jobId={}, status={}", scheduleId, jobId, status);
        repository.recordOutcome(scheduleId, jobId, status.name(), completedAt);
        log.debug("recordOutcome() | return=void");
    }

    @Override
    @Transactional
    public void deactivate(String scheduleId, String reason) {
        log.debug("deactivate() | scheduleId={}, reason={}", scheduleId, reason);
        repository.deactivate(scheduleId);
        log.debug("deactivate() | return=void");
    }

    @Override
    @Transactional
    public void delete(String scheduleId, String ownerEmail) {
        log.debug("delete() | scheduleId={}, ownerEmail={}", scheduleId, ownerEmail);
        repository.deleteByScheduleIdAndOwnerEmail(scheduleId, ownerEmail);
        log.debug("delete() | return=void");
    }

    // ---- mapping helpers ----

    private EnterprisePushScheduleEntity toEntity(DataPushSchedule s) {
        EnterprisePushScheduleEntity e = new EnterprisePushScheduleEntity();
        e.setScheduleId(s.scheduleId());
        e.setOwnerEmail(s.ownerEmail());
        e.setLabel(s.label());
        e.setFeedId(s.feedId());
        e.setPromptId(s.promptId());
        e.setPromptText(s.promptText());
        e.setParametersJson(toJson(s.parameters()));
        e.setFormat(s.format().name());
        e.setCronExpression(s.cronExpression());
        e.setZoneId(s.zoneId());
        e.setRecipients(String.join("|", s.recipients()));
        e.setActive(s.active());
        e.setNextRunAt(s.nextRunAt());
        e.setLastRunAt(s.lastRunAt());
        e.setLastStatus(s.lastStatus() != null ? s.lastStatus().name() : null);
        e.setLastJobId(s.lastJobId());
        e.setConsecutiveFailures(s.consecutiveFailures());
        e.setCreatedAt(s.createdAt());
        e.setUpdatedAt(s.updatedAt());
        return e;
    }

    private DataPushSchedule toDomain(EnterprisePushScheduleEntity e) {
        return new DataPushSchedule(
                e.getScheduleId(),
                e.getOwnerEmail(),
                e.getLabel(),
                e.getFeedId(),
                e.getPromptId(),
                e.getPromptText(),
                fromJson(e.getParametersJson()),
                ExportFormat.valueOf(e.getFormat()),
                e.getCronExpression(),
                e.getZoneId(),
                PipeDelimitedUtils.split(e.getRecipients()),
                e.isActive(),
                e.getNextRunAt(),
                e.getLastRunAt(),
                e.getLastStatus() != null ? DataJobStatus.valueOf(e.getLastStatus()) : null,
                e.getLastJobId(),
                e.getConsecutiveFailures(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("toJson() | failed to serialize parameters: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("fromJson() | failed to deserialize parameters: {}", e.getMessage());
            return Map.of();
        }
    }
}
