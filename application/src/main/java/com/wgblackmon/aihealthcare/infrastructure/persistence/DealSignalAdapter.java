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
 * <p>{@code saveAll} persists each signal individually rather than in one
 * batch — LLM-extracted fields (company/counterparty names in particular)
 * are freeform text with no length guarantee, and a single oversized value
 * must not roll back every other signal detected in the same pipeline run.
 *
 * @author  Bill Blackmon
 * @version 1.3
 * @since   2026-08-04
 * @updated 2026-09-05
 */
@Slf4j
@Component
public class DealSignalAdapter implements DealSignalPort {

    private static final int ARTICLE_ID_MAX_LENGTH = 500;
    private static final int TITLE_MAX_LENGTH = 1000;
    private static final int COMPANY_NAME_MAX_LENGTH = 500;
    private static final int DEAL_AMOUNT_MAX_LENGTH = 100;
    private static final int COUNTERPARTY_NAME_MAX_LENGTH = 500;
    private static final int SOURCE_URL_MAX_LENGTH = 2048;

    private final DealSignalRepository repository;

    public DealSignalAdapter(DealSignalRepository repository) {
        log.debug("DealSignalAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void saveAll(List<DealSignal> signals) {
        log.debug("saveAll() | signals={}", signals.size());
        int saved = 0;
        for (DealSignal signal : signals) {
            try {
                repository.save(toEntity(signal));
                saved++;
            } catch (RuntimeException e) {
                log.warn("saveAll() | failed to persist signal {} — skipping. cause={}",
                        signal.signalId(), e.getMessage());
            }
        }
        log.debug("saveAll() | return=void, saved={}/{}", saved, signals.size());
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
        entity.setArticleId(truncate(signal.signalId(), signal.articleId(), "articleId", ARTICLE_ID_MAX_LENGTH));
        entity.setTitle(truncate(signal.signalId(), signal.title(), "title", TITLE_MAX_LENGTH));
        entity.setSignalType(signal.signalType().name());
        entity.setCompanyName(truncate(signal.signalId(), signal.companyName(), "companyName", COMPANY_NAME_MAX_LENGTH));
        entity.setSummary(signal.summary());
        entity.setConfidence(signal.confidence());
        entity.setDetectedAt(signal.detectedAt());
        entity.setDealAmount(truncate(signal.signalId(), signal.dealAmount(), "dealAmount", DEAL_AMOUNT_MAX_LENGTH));
        entity.setCounterpartyName(truncate(signal.signalId(), signal.counterpartyName(), "counterpartyName", COUNTERPARTY_NAME_MAX_LENGTH));
        entity.setSourceUrl(truncate(signal.signalId(), signal.sourceUrl(), "sourceUrl", SOURCE_URL_MAX_LENGTH));
        entity.setLlmAnalysis(signal.llmAnalysis());
        return entity;
    }

    /**
     * Clips a value to the database column's max length, logging a warning
     * when clipping actually occurs.
     *
     * <p>LLM-extracted fields (company/counterparty names, deal amounts) are
     * freeform text with no length guarantee — a malformed response can slip
     * a full sentence into a field meant for a short label. Truncating here
     * keeps the signal instead of losing the whole insert to a
     * {@code DataIntegrityViolationException}.
     *
     * @param signalId  the owning signal's id, for the warning log
     * @param value     the value to clip; null passes through unchanged
     * @param fieldName the column name, for the warning log
     * @param maxLength the column's max length
     * @return the value, clipped to {@code maxLength} characters if needed
     */
    private String truncate(String signalId, String value, String fieldName, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.warn("truncate() | signal {} field '{}' is {} chars, exceeding column limit of {} — clipping",
                signalId, fieldName, value.length(), maxLength);
        return value.substring(0, maxLength);
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
