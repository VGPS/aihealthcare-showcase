package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link RegulatoryEventPort}.
 *
 * <p>Converts between the immutable {@link RegulatoryEvent} domain record
 * and the mutable {@link RegulatoryEventEntity} JPA entity. The
 * {@code aiHealthcareKeywords} list is stored as a pipe-delimited string.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
@Component
public class RegulatoryEventAdapter implements RegulatoryEventPort {

    private final RegulatoryEventRepository repository;

    public RegulatoryEventAdapter(RegulatoryEventRepository repository) {
        log.debug("RegulatoryEventAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(RegulatoryEvent event) {
        log.debug("save() | eventId={}, eventType={}", event.eventId(), event.eventType());
        repository.save(toEntity(event));
        log.debug("save() | return=void");
    }

    @Override
    public void saveAll(List<RegulatoryEvent> events) {
        log.debug("saveAll() | count={}", events.size());
        List<RegulatoryEventEntity> entities = new ArrayList<>();
        for (RegulatoryEvent event : events) {
            entities.add(toEntity(event));
        }
        repository.saveAll(entities);
        log.debug("saveAll() | return=void");
    }

    @Override
    public boolean existsByReferenceNumber(String referenceNumber) {
        log.debug("existsByReferenceNumber() | referenceNumber={}", referenceNumber);
        boolean result = repository.existsByReferenceNumber(referenceNumber);
        log.debug("existsByReferenceNumber() | return={}", result);
        return result;
    }

    @Override
    public boolean existsBySourceUrl(String sourceUrl) {
        log.debug("existsBySourceUrl() | sourceUrl={}", sourceUrl);
        boolean result = repository.existsBySourceUrl(sourceUrl);
        log.debug("existsBySourceUrl() | return={}", result);
        return result;
    }

    @Override
    public List<RegulatoryEvent> findRecent(int limit) {
        log.debug("findRecent() | limit={}", limit);
        List<RegulatoryEventEntity> entities = repository.findAllByOrderByDiscoveredAtDesc();
        List<RegulatoryEvent> result = toLimitedDomainList(entities, limit);
        log.debug("findRecent() | return={} events", result.size());
        return result;
    }

    @Override
    public List<RegulatoryEvent> findByType(RegulatoryEventType type, int limit) {
        log.debug("findByType() | type={}, limit={}", type, limit);
        List<RegulatoryEventEntity> entities = repository.findByEventTypeOrderByDiscoveredAtDesc(type.name());
        List<RegulatoryEvent> result = toLimitedDomainList(entities, limit);
        log.debug("findByType() | return={} events", result.size());
        return result;
    }

    @Override
    public List<RegulatoryEvent> findByBody(RegulatoryBody body, int limit) {
        log.debug("findByBody() | body={}, limit={}", body, limit);
        List<RegulatoryEventEntity> entities = repository.findByRegulatoryBodyOrderByDiscoveredAtDesc(body.name());
        List<RegulatoryEvent> result = toLimitedDomainList(entities, limit);
        log.debug("findByBody() | return={} events", result.size());
        return result;
    }

    @Override
    public Optional<RegulatoryEvent> findById(String eventId) {
        log.debug("findById() | eventId={}", eventId);
        Optional<RegulatoryEvent> result = repository.findById(eventId).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public List<RegulatoryEvent> findByKeyword(String keyword, int limit) {
        log.debug("findByKeyword() | keyword={}, limit={}", keyword, limit);
        List<RegulatoryEventEntity> entities = repository.findByKeyword(keyword);
        List<RegulatoryEvent> result = toLimitedDomainList(entities, limit);
        log.debug("findByKeyword() | return={} events", result.size());
        return result;
    }

    private List<RegulatoryEvent> toLimitedDomainList(List<RegulatoryEventEntity> entities, int limit) {
        List<RegulatoryEvent> result = new ArrayList<>();
        int count = 0;
        for (RegulatoryEventEntity entity : entities) {
            if (count >= limit) {
                break;
            }
            result.add(toDomain(entity));
            count++;
        }
        return result;
    }

    private RegulatoryEventEntity toEntity(RegulatoryEvent event) {
        RegulatoryEventEntity entity = new RegulatoryEventEntity();
        entity.setEventId(event.eventId());
        entity.setEventType(event.eventType().name());
        entity.setRegulatoryBody(event.regulatoryBody().name());
        entity.setTitle(event.title());
        entity.setSummary(event.summary());
        entity.setReferenceNumber(event.referenceNumber());
        entity.setApplicantName(event.applicantName());
        entity.setDeviceName(event.deviceName());
        entity.setSourceUrl(event.sourceUrl());
        entity.setLinkedArticleId(event.linkedArticleId());
        entity.setPublishedAt(event.publishedAt());
        entity.setDiscoveredAt(event.discoveredAt());
        entity.setAiHealthcareKeywords(joinKeywords(event.aiHealthcareKeywords()));
        return entity;
    }

    private RegulatoryEvent toDomain(RegulatoryEventEntity entity) {
        return new RegulatoryEvent(
                entity.getEventId(),
                RegulatoryEventType.valueOf(entity.getEventType()),
                RegulatoryBody.valueOf(entity.getRegulatoryBody()),
                entity.getTitle(),
                entity.getSummary(),
                entity.getReferenceNumber(),
                entity.getApplicantName(),
                entity.getDeviceName(),
                entity.getSourceUrl(),
                entity.getLinkedArticleId(),
                entity.getPublishedAt(),
                entity.getDiscoveredAt(),
                splitKeywords(entity.getAiHealthcareKeywords())
        );
    }

    private String joinKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                sb.append("|");
            }
            sb.append(keywords.get(i));
        }
        return sb.toString();
    }

    private List<String> splitKeywords(String pipeDelimited) {
        if (pipeDelimited == null || pipeDelimited.isBlank()) {
            return List.of();
        }
        return Arrays.asList(pipeDelimited.split("\\|"));
    }
}
