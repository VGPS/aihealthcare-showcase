package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompanyEvent;
import com.wgblackmon.aihealthcare.domain.model.CompanyEventType;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link CompanyEventPort}.
 *
 * <p>Maps between {@link CompanyEvent} domain records and
 * {@link CompanyEventEntity} JPA entities.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
@Component
public class CompanyEventAdapter implements CompanyEventPort {

    private final CompanyEventRepository repository;

    public CompanyEventAdapter(CompanyEventRepository repository) {
        log.debug("CompanyEventAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(CompanyEvent event) {
        log.debug("save() | eventId={}, companySlug={}, eventType={}",
                  event.eventId(), event.companySlug(), event.eventType());

        CompanyEventEntity entity = toEntity(event);
        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public List<CompanyEvent> findByCompanySlug(String slug) {
        log.debug("findByCompanySlug() | slug={}", slug);

        List<CompanyEventEntity> entities = repository.findByCompanySlugOrderByOccurredAtDesc(slug);
        List<CompanyEvent> result = new ArrayList<>();
        for (CompanyEventEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findByCompanySlug() | return={} events", result.size());
        return result;
    }

    @Override
    public List<CompanyEvent> findRecent(int limit) {
        log.debug("findRecent() | limit={}", limit);

        List<CompanyEventEntity> entities = repository.findTop20ByOrderByDetectedAtDesc();
        List<CompanyEvent> result = new ArrayList<>();
        int count = 0;
        for (CompanyEventEntity entity : entities) {
            if (count >= limit) break;
            result.add(toDomain(entity));
            count++;
        }

        log.debug("findRecent() | return={} events", result.size());
        return result;
    }

    private CompanyEventEntity toEntity(CompanyEvent event) {
        CompanyEventEntity entity = new CompanyEventEntity();
        entity.setEventId(event.eventId());
        entity.setCompanySlug(event.companySlug());
        entity.setEventType(event.eventType().name());
        entity.setTitle(event.title());
        entity.setDescription(event.description());
        entity.setSourceArticleId(event.sourceArticleId());
        entity.setOccurredAt(event.occurredAt());
        entity.setDetectedAt(event.detectedAt());
        return entity;
    }

    private CompanyEvent toDomain(CompanyEventEntity entity) {
        return new CompanyEvent(
                entity.getEventId(),
                entity.getCompanySlug(),
                CompanyEventType.valueOf(entity.getEventType()),
                entity.getTitle(),
                entity.getDescription(),
                entity.getSourceArticleId(),
                entity.getOccurredAt(),
                entity.getDetectedAt()
        );
    }
}
