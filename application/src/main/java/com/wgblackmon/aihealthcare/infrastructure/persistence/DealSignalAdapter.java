package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealSignalPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link DealSignalPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class DealSignalAdapter implements DealSignalPort {

    private final DealSignalRepository repository;

    public DealSignalAdapter(DealSignalRepository repository) {
        log.debug("DealSignalAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void saveAll(List<DealSignal> signals) {
        log.debug("saveAll() | signals={}", signals.size());
        List<DealSignalEntity> entities = new ArrayList<>();
        for (DealSignal signal : signals) {
            entities.add(toEntity(signal));
        }
        repository.saveAll(entities);
        log.debug("saveAll() | return=void");
    }

    @Override
    public List<DealSignal> findRecent(int limit) {
        log.debug("findRecent() | limit={}", limit);
        List<DealSignalEntity> entities = repository.findAllByOrderByDetectedAtDesc(PageRequest.of(0, limit));
        List<DealSignal> result = new ArrayList<>();
        for (DealSignalEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findRecent() | return={} signals", result.size());
        return result;
    }

    @Override
    public boolean existsByArticleId(String articleId) {
        log.debug("existsByArticleId() | articleId={}", articleId);
        boolean result = repository.existsByArticleId(articleId);
        log.debug("existsByArticleId() | return={}", result);
        return result;
    }

    private DealSignalEntity toEntity(DealSignal signal) {
        DealSignalEntity entity = new DealSignalEntity();
        entity.setSignalId(signal.signalId());
        entity.setArticleId(signal.articleId());
        entity.setTitle(signal.title());
        entity.setSignalType(signal.signalType().name());
        entity.setCompanyName(signal.companyName());
        entity.setSummary(signal.summary());
        entity.setConfidence(signal.confidence());
        entity.setDetectedAt(signal.detectedAt());
        return entity;
    }

    private DealSignal toDomain(DealSignalEntity entity) {
        return new DealSignal(
                entity.getSignalId(),
                entity.getArticleId(),
                entity.getTitle(),
                DealSignalType.valueOf(entity.getSignalType()),
                entity.getCompanyName(),
                entity.getSummary(),
                entity.getConfidence(),
                entity.getDetectedAt()
        );
    }
}
