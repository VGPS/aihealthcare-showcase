package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.LawChangeEvent;
import com.wgblackmon.aihealthcare.domain.model.LawSource;
import com.wgblackmon.aihealthcare.domain.model.LawStatus;
import com.wgblackmon.aihealthcare.domain.model.SourceType;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;
import com.wgblackmon.aihealthcare.domain.port.outbound.LawChangeEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.StateLawPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link StateLawPort} and {@link LawChangeEventPort}.
 *
 * <p>Converts between immutable domain records and mutable JPA entities for the
 * state health-AI legislation registry. {@link StateLaw#categories()} are stored
 * as pipe-delimited enum name strings. {@link StateLaw#sources()} are stored as
 * child rows in the {@code state_law_sources} table, replaced in full on each
 * upsert.
 *
 * <p>{@link com.wgblackmon.aihealthcare.domain.port.outbound.NewBillCandidatePort}
 * is implemented by the separate {@link NewBillCandidateAdapter} because Java
 * cannot merge the conflicting {@code findUnreviewed()} return types from both
 * ports into one class.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
@Slf4j
@Component
public class StateLawAdapter implements StateLawPort, LawChangeEventPort {

    private final StateLawRepository lawRepository;
    private final StateLawSourceRepository sourceRepository;
    private final LawChangeEventRepository changeEventRepository;

    public StateLawAdapter(StateLawRepository lawRepository,
                           StateLawSourceRepository sourceRepository,
                           LawChangeEventRepository changeEventRepository) {
        log.debug("StateLawAdapter() | lawRepository={}, sourceRepository={}, changeEventRepository={}",
                lawRepository.getClass().getSimpleName(),
                sourceRepository.getClass().getSimpleName(),
                changeEventRepository.getClass().getSimpleName());
        this.lawRepository = lawRepository;
        this.sourceRepository = sourceRepository;
        this.changeEventRepository = changeEventRepository;
    }

    // ── StateLawPort ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void upsert(StateLaw law) {
        log.debug("upsert() | id={}, stateCode={}, status={}", law.id(), law.stateCode(), law.status());
        StateLawEntity entity = toEntity(law);
        lawRepository.save(entity);

        sourceRepository.deleteByLawId(law.id());
        for (LawSource source : law.sources()) {
            StateLawSourceEntity sourceEntity = toSourceEntity(law.id(), source);
            sourceRepository.save(sourceEntity);
        }
        log.debug("upsert() | return=void, sourcesWritten={}", law.sources().size());
    }

    @Override
    public Optional<StateLaw> findById(String id) {
        log.debug("findById() | id={}", id);
        Optional<StateLaw> result = lawRepository.findById(id)
                .map(entity -> {
                    List<StateLawSourceEntity> sources = sourceRepository.findByLawId(entity.getId());
                    return toDomain(entity, sources);
                });
        log.debug("findById() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public List<StateLaw> findAll() {
        log.debug("findAll()");
        List<StateLawEntity> entities = lawRepository.findAll();
        List<StateLaw> result = toDomainList(entities);
        log.debug("findAll() | return={} laws", result.size());
        return result;
    }

    @Override
    public List<StateLaw> findByState(StateCode stateCode) {
        log.debug("findByState() | stateCode={}", stateCode);
        List<StateLawEntity> entities = lawRepository.findByStateCode(stateCode.name());
        List<StateLaw> result = toDomainList(entities);
        log.debug("findByState() | return={} laws", result.size());
        return result;
    }

    @Override
    public List<StateLaw> findByCategory(LawCategory category) {
        log.debug("findByCategory() | category={}", category);
        List<StateLawEntity> entities = lawRepository.findByCategoriesContaining(category.name());
        List<StateLaw> result = toDomainList(entities);
        log.debug("findByCategory() | return={} laws", result.size());
        return result;
    }

    @Override
    public List<StateLaw> findByStatus(LawStatus status) {
        log.debug("findByStatus() | status={}", status);
        List<StateLawEntity> entities = lawRepository.findByStatus(status.name());
        List<StateLaw> result = toDomainList(entities);
        log.debug("findByStatus() | return={} laws", result.size());
        return result;
    }

    @Override
    public List<StateLaw> findEffectiveBetween(String from, String to) {
        log.debug("findEffectiveBetween() | from={}, to={}", from, to);
        List<StateLawEntity> allEntities = lawRepository.findAll();
        List<StateLaw> result = new ArrayList<>();
        for (StateLawEntity entity : allEntities) {
            String effectiveDate = entity.getEffectiveDate();
            if (effectiveDate != null && !effectiveDate.isBlank()) {
                if (effectiveDate.compareTo(from) >= 0 && effectiveDate.compareTo(to) <= 0) {
                    List<StateLawSourceEntity> sources = sourceRepository.findByLawId(entity.getId());
                    result.add(toDomain(entity, sources));
                }
            }
        }
        log.debug("findEffectiveBetween() | return={} laws", result.size());
        return result;
    }

    @Override
    public List<StateLaw> search(String query) {
        log.debug("search() | query={}", query);
        List<StateLawEntity> entities = lawRepository.search(query);
        List<StateLaw> result = toDomainList(entities);
        log.debug("search() | return={} laws", result.size());
        return result;
    }

    @Override
    @Transactional
    public void updateSourceMonitoringFields(String lawId, String sourceUrl,
                                              java.time.Instant fetchedAt, String contentHash,
                                              Integer httpStatus, boolean changed) {
        log.debug("updateSourceMonitoringFields() | lawId={}, sourceUrl={}, changed={}",
                  lawId, sourceUrl, changed);
        sourceRepository.findByLawIdAndUrl(lawId, sourceUrl).ifPresent(entity -> {
            entity.setLastFetchedAt(fetchedAt);
            entity.setLastContentHash(contentHash);
            entity.setLastHttpStatus(httpStatus);
            entity.setChangedSinceLastReview(changed);
            sourceRepository.save(entity);
        });
        log.debug("updateSourceMonitoringFields() | return=void");
    }

    // ── LawChangeEventPort ────────────────────────────────────────────────────

    @Override
    public void recordChangeEvent(LawChangeEvent event) {
        log.debug("recordChangeEvent() | lawId={}, changeType={}", event.lawId(), event.changeType());
        LawChangeEventEntity entity = toChangeEventEntity(event);
        changeEventRepository.save(entity);
        log.debug("recordChangeEvent() | return=void");
    }

    @Override
    public List<LawChangeEvent> findUnreviewed() {
        log.debug("findUnreviewed()");
        List<LawChangeEventEntity> entities = changeEventRepository.findByReviewedFalseOrderByDetectedAtDesc();
        List<LawChangeEvent> result = new ArrayList<>();
        for (LawChangeEventEntity entity : entities) {
            result.add(toChangeEventDomain(entity));
        }
        log.debug("findUnreviewed() | return={} events", result.size());
        return result;
    }

    @Override
    public void markReviewed(Long eventId) {
        log.debug("markReviewed() | eventId={}", eventId);
        changeEventRepository.findById(eventId).ifPresent(entity -> {
            entity.setReviewed(true);
            changeEventRepository.save(entity);
        });
        log.debug("markReviewed() | return=void");
    }

    // ── Private conversion helpers ────────────────────────────────────────────

    private List<StateLaw> toDomainList(List<StateLawEntity> entities) {
        List<StateLaw> result = new ArrayList<>();
        for (StateLawEntity entity : entities) {
            List<StateLawSourceEntity> sources = sourceRepository.findByLawId(entity.getId());
            result.add(toDomain(entity, sources));
        }
        return result;
    }

    private StateLaw toDomain(StateLawEntity entity, List<StateLawSourceEntity> sourceEntities) {
        List<LawCategory> categories = parseCategories(entity.getCategories());
        List<LawSource> sources = new ArrayList<>();
        for (StateLawSourceEntity se : sourceEntities) {
            sources.add(toSourceDomain(se));
        }
        return new StateLaw(
                entity.getId(),
                StateCode.valueOf(entity.getStateCode()),
                entity.getStateName(),
                entity.getBillNumber(),
                entity.getTitle(),
                entity.getYearEnacted(),
                entity.getDateSigned(),
                entity.getDateSignedNote(),
                entity.getEffectiveDate(),
                entity.getEffectiveDateNote(),
                LawStatus.valueOf(entity.getStatus()),
                entity.getStatusDetail(),
                categories,
                entity.getRegulatedParties(),
                entity.getKeyRequirements(),
                entity.getEnforcement(),
                sources,
                entity.getNotes(),
                entity.getDatasetVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private StateLawEntity toEntity(StateLaw law) {
        StateLawEntity entity = new StateLawEntity();
        entity.setId(law.id());
        entity.setStateCode(law.stateCode().name());
        entity.setStateName(law.stateName());
        entity.setBillNumber(law.billNumber());
        entity.setTitle(law.title());
        entity.setYearEnacted(law.yearEnacted());
        entity.setDateSigned(law.dateSigned());
        entity.setDateSignedNote(law.dateSignedNote());
        entity.setEffectiveDate(law.effectiveDate());
        entity.setEffectiveDateNote(law.effectiveDateNote());
        entity.setStatus(law.status().name());
        entity.setStatusDetail(law.statusDetail());
        entity.setCategories(joinCategories(law.categories()));
        entity.setRegulatedParties(law.regulatedParties());
        entity.setKeyRequirements(law.keyRequirements());
        entity.setEnforcement(law.enforcement());
        entity.setNotes(law.notes());
        entity.setDatasetVersion(law.datasetVersion());
        entity.setCreatedAt(law.createdAt());
        entity.setUpdatedAt(law.updatedAt());
        return entity;
    }

    private StateLawSourceEntity toSourceEntity(String lawId, LawSource source) {
        StateLawSourceEntity entity = new StateLawSourceEntity();
        entity.setLawId(lawId);
        entity.setSourceType(source.sourceType().name());
        entity.setUrl(source.url());
        entity.setLastFetchedAt(source.lastFetchedAt());
        entity.setLastContentHash(source.lastContentHash());
        entity.setLastHttpStatus(source.lastHttpStatus());
        entity.setChangedSinceLastReview(source.changedSinceLastReview());
        return entity;
    }

    private LawSource toSourceDomain(StateLawSourceEntity entity) {
        return new LawSource(
                SourceType.valueOf(entity.getSourceType()),
                entity.getUrl(),
                entity.getLastFetchedAt(),
                entity.getLastContentHash(),
                entity.getLastHttpStatus(),
                entity.isChangedSinceLastReview()
        );
    }

    private LawChangeEventEntity toChangeEventEntity(LawChangeEvent event) {
        LawChangeEventEntity entity = new LawChangeEventEntity();
        if (event.id() != null) {
            entity.setId(event.id());
        }
        entity.setLawId(event.lawId());
        entity.setDetectedAt(event.detectedAt());
        entity.setChangeType(event.changeType());
        entity.setDetail(event.detail());
        entity.setReviewed(event.reviewed());
        return entity;
    }

    private LawChangeEvent toChangeEventDomain(LawChangeEventEntity entity) {
        return new LawChangeEvent(
                entity.getId(),
                entity.getLawId(),
                entity.getDetectedAt(),
                entity.getChangeType(),
                entity.getDetail(),
                entity.isReviewed()
        );
    }

    private String joinCategories(List<LawCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < categories.size(); i++) {
            if (i > 0) {
                sb.append("|");
            }
            sb.append(categories.get(i).name());
        }
        return sb.toString();
    }

    private List<LawCategory> parseCategories(String pipeDelimited) {
        if (pipeDelimited == null || pipeDelimited.isBlank()) {
            return List.of();
        }
        List<LawCategory> result = new ArrayList<>();
        for (String token : pipeDelimited.split("\\|")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                try {
                    result.add(LawCategory.valueOf(trimmed));
                } catch (IllegalArgumentException e) {
                    log.warn("parseCategories() | unknown LawCategory '{}' — skipping", trimmed);
                }
            }
        }
        return result;
    }
}
