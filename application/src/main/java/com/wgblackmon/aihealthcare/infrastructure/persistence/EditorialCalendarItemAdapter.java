package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.EditorialDemandSignal;
import com.wgblackmon.aihealthcare.domain.model.EditorialEffort;
import com.wgblackmon.aihealthcare.domain.model.EditorialItem;
import com.wgblackmon.aihealthcare.domain.model.EditorialPriority;
import com.wgblackmon.aihealthcare.domain.model.EditorialSource;
import com.wgblackmon.aihealthcare.domain.model.EditorialStatus;
import com.wgblackmon.aihealthcare.domain.model.EditorialTheme;
import com.wgblackmon.aihealthcare.domain.port.outbound.EditorialCalendarPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link EditorialCalendarPort}.
 *
 * <p>Converts between the JPA {@link EditorialCalendarItemEntity} and the
 * domain {@link EditorialItem} record. Audiences are stored as a
 * pipe-delimited string; primary sources are stored as a JSON TEXT column
 * serialized by Jackson.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@Slf4j
@Component
public class EditorialCalendarItemAdapter implements EditorialCalendarPort {

    private static final String PIPE = "|";
    private static final TypeReference<List<Map<String, String>>> SOURCE_LIST_TYPE =
            new TypeReference<>() {};

    private final EditorialCalendarItemRepository repository;
    private final ObjectMapper objectMapper;

    public EditorialCalendarItemAdapter(EditorialCalendarItemRepository repository,
                                        ObjectMapper objectMapper) {
        log.debug("EditorialCalendarItemAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void upsert(EditorialItem item) {
        log.debug("upsert() | id={}", item.id());
        repository.save(toEntity(item));
        log.debug("upsert() | return=void");
    }

    @Override
    public List<EditorialItem> findAll() {
        log.debug("findAll()");
        List<EditorialItem> result = repository.findAll().stream()
                .map(this::toDomain)
                .toList();
        log.debug("findAll() | return={}", result.size());
        return result;
    }

    @Override
    public Optional<EditorialItem> findById(String id) {
        log.debug("findById() | id={}", id);
        Optional<EditorialItem> result = repository.findById(id).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent());
        return result;
    }

    @Override
    public List<EditorialItem> findByStatus(EditorialStatus status) {
        log.debug("findByStatus() | status={}", status);
        List<EditorialItem> result = repository
                .findByStatusOrderByPriorityTierAscPreferredDateAsc(status.name())
                .stream()
                .map(this::toDomain)
                .toList();
        log.debug("findByStatus() | return={}", result.size());
        return result;
    }

    @Override
    public List<EditorialItem> findByPriority(EditorialPriority priority) {
        log.debug("findByPriority() | priority={}", priority);
        List<EditorialItem> result = repository
                .findByPriorityTierOrderByPreferredDateAsc(priority.name())
                .stream()
                .map(this::toDomain)
                .toList();
        log.debug("findByPriority() | return={}", result.size());
        return result;
    }

    @Override
    public void updateStatus(String id, EditorialStatus status) {
        log.debug("updateStatus() | id={}, status={}", id, status);
        repository.findById(id).ifPresent(entity -> {
            entity.setStatus(status.name());
            repository.save(entity);
        });
        log.debug("updateStatus() | return=void");
    }

    @Override
    public Optional<EditorialItem> findNext() {
        log.debug("findNext()");
        Optional<EditorialItem> result = repository
                .findFirstByStatusOrderByPriorityTierAscPreferredDateAsc(EditorialStatus.PLANNED.name())
                .map(this::toDomain);
        log.debug("findNext() | return={}", result.isPresent());
        return result;
    }

    @Override
    public List<EditorialItem> findBySeries(String series) {
        log.debug("findBySeries() | series={}", series);
        List<EditorialItem> result = repository.findBySeriesOrderByPreferredDateAsc(series)
                .stream()
                .map(this::toDomain)
                .toList();
        log.debug("findBySeries() | return={}", result.size());
        return result;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private EditorialCalendarItemEntity toEntity(EditorialItem item) {
        EditorialCalendarItemEntity e = new EditorialCalendarItemEntity();
        e.setId(item.id());
        e.setTitle(item.title());
        e.setHook(item.hook());
        e.setTheme(item.theme() != null ? item.theme().name() : null);
        e.setDemandSignal(item.demandSignal() != null ? item.demandSignal().name() : null);
        e.setPriorityTier(item.priorityTier().name());
        e.setEffort(item.effort() != null ? item.effort().name() : null);
        e.setFormat(item.format());
        e.setPublishWindowStart(item.publishWindowStart());
        e.setPublishWindowEnd(item.publishWindowEnd());
        e.setPreferredDate(item.preferredDate());
        e.setCta(item.cta());
        e.setStatus(item.status().name());
        e.setLastVerified(item.lastVerified());
        e.setAudiences(item.audiences().isEmpty() ? null : String.join(PIPE, item.audiences()));
        e.setPrimarySourcesJson(serializeSources(item.primarySources()));
        e.setSeries(item.series());
        return e;
    }

    private EditorialItem toDomain(EditorialCalendarItemEntity e) {
        List<String> audiences = e.getAudiences() == null || e.getAudiences().isBlank()
                ? List.of()
                : Arrays.asList(e.getAudiences().split("\\|"));
        return new EditorialItem(
                e.getId(),
                e.getTitle(),
                e.getHook(),
                e.getTheme() != null ? EditorialTheme.valueOf(e.getTheme()) : null,
                e.getDemandSignal() != null ? EditorialDemandSignal.valueOf(e.getDemandSignal()) : null,
                EditorialPriority.valueOf(e.getPriorityTier()),
                e.getEffort() != null ? EditorialEffort.valueOf(e.getEffort()) : null,
                e.getFormat(),
                e.getPublishWindowStart(),
                e.getPublishWindowEnd(),
                e.getPreferredDate(),
                e.getCta(),
                EditorialStatus.valueOf(e.getStatus()),
                e.getLastVerified(),
                audiences,
                deserializeSources(e.getPrimarySourcesJson()),
                e.getSeries());
    }

    private String serializeSources(List<EditorialSource> sources) {
        if (sources == null || sources.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException ex) {
            log.warn("serializeSources() | serialization failed: {}", ex.getMessage());
            return "[]";
        }
    }

    private List<EditorialSource> deserializeSources(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return List.of();
        try {
            List<Map<String, String>> raw = objectMapper.readValue(json, SOURCE_LIST_TYPE);
            return raw.stream()
                    .map(m -> new EditorialSource(m.get("url"), m.get("label"), m.get("sourceType")))
                    .toList();
        } catch (JsonProcessingException ex) {
            log.warn("deserializeSources() | deserialization failed: {}", ex.getMessage());
            return List.of();
        }
    }
}
