package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealSignalPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.DealSignalListView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA-backed implementation of {@link DealSignalPort}.
 *
 * <p>List queries ({@code findRecent}, {@code findByType}) use the
 * {@link DealSignalListView} projection to skip the {@code llm_analysis}
 * TEXT column. Detail lookups ({@code findById}) use the full entity.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-08-04
 * @updated 2026-08-26
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
    public List<DealSignal> findRecent(int pageSize, int page) {
        log.debug("findRecent() | pageSize={}, page={}", pageSize, page);
        List<DealSignalListView> views = repository.findRecentListView(PageRequest.of(page, pageSize));
        List<DealSignal> result = new ArrayList<>();
        for (DealSignalListView view : views) {
            result.add(toDomainList(view));
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

    @Override
    public DealSignal findById(String signalId) {
        log.debug("findById() | signalId={}", signalId);
        DealSignal result = repository.findById(signalId)
                .map(this::toDomain)
                .orElse(null);
        log.debug("findById() | return={}", result != null ? result.signalId() : "null");
        return result;
    }

    @Override
    public List<DealSignal> findByType(String signalType, int pageSize, int page) {
        log.debug("findByType() | signalType={}, pageSize={}, page={}", signalType, pageSize, page);
        List<DealSignalListView> views = repository.findBySignalTypeListView(
                signalType, PageRequest.of(page, pageSize));
        List<DealSignal> result = new ArrayList<>();
        for (DealSignalListView view : views) {
            result.add(toDomainList(view));
        }
        log.debug("findByType() | return={} signals", result.size());
        return result;
    }

    @Override
    public Map<String, Long> countByTypeInPeriod(Instant from, Instant to) {
        log.debug("countByTypeInPeriod() | from={}, to={}", from, to);
        List<Object[]> rows = repository.countBySignalTypeInPeriod(from, to);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], (Long) row[1]);
        }
        log.debug("countByTypeInPeriod() | return={}", result);
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
        entity.setDealAmount(signal.dealAmount());
        entity.setCounterpartyName(signal.counterpartyName());
        entity.setSourceUrl(signal.sourceUrl());
        entity.setLlmAnalysis(signal.llmAnalysis());
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
                entity.getDetectedAt(),
                entity.getDealAmount(),
                entity.getCounterpartyName(),
                entity.getSourceUrl(),
                entity.getLlmAnalysis()
        );
    }

    private DealSignal toDomainList(DealSignalListView view) {
        return new DealSignal(
                view.getSignalId(),
                view.getArticleId(),
                view.getTitle(),
                DealSignalType.valueOf(view.getSignalType()),
                view.getCompanyName(),
                view.getSummary(),
                view.getConfidence(),
                view.getDetectedAt(),
                view.getDealAmount(),
                view.getCounterpartyName(),
                view.getSourceUrl(),
                null
        );
    }
}
