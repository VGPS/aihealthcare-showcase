package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialPhase;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link ClinicalTrialPort}.
 *
 * <p>Converts between the immutable {@link ClinicalTrial} domain record
 * and the mutable {@link ClinicalTrialEntity} JPA entity. The
 * {@code conditions} and {@code aiHealthcareKeywords} lists are stored
 * as pipe-delimited strings.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
@Slf4j
@Component
public class ClinicalTrialAdapter implements ClinicalTrialPort {

    private static final int TITLE_MAX_LENGTH = 1000;
    private static final int SPONSOR_MAX_LENGTH = 500;
    private static final int SOURCE_URL_MAX_LENGTH = 2048;

    private final ClinicalTrialRepository repository;

    public ClinicalTrialAdapter(ClinicalTrialRepository repository) {
        log.debug("ClinicalTrialAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(ClinicalTrial trial) {
        log.debug("save() | trialId={}, nctId={}", trial.trialId(), trial.nctId());
        repository.save(toEntity(trial));
        log.debug("save() | return=void");
    }

    @Override
    public void saveAll(List<ClinicalTrial> trials) {
        log.debug("saveAll() | count={}", trials.size());
        int saved = 0;
        for (ClinicalTrial trial : trials) {
            try {
                repository.save(toEntity(trial));
                saved++;
            } catch (RuntimeException e) {
                log.warn("saveAll() | failed to persist trial {} — skipping. cause={}",
                        trial.trialId(), e.getMessage());
            }
        }
        log.debug("saveAll() | return=void, saved={}/{}", saved, trials.size());
    }

    @Override
    public boolean existsByNctId(String nctId) {
        log.debug("existsByNctId() | nctId={}", nctId);
        boolean result = repository.existsByNctId(nctId);
        log.debug("existsByNctId() | return={}", result);
        return result;
    }

    @Override
    public List<ClinicalTrial> findRecent(int limit) {
        log.debug("findRecent() | limit={}", limit);
        List<ClinicalTrialEntity> entities = repository.findAllByOrderByDiscoveredAtDesc();
        List<ClinicalTrial> result = toLimitedDomainList(entities, limit);
        log.debug("findRecent() | return={} trials", result.size());
        return result;
    }

    @Override
    public List<ClinicalTrial> findByStatus(ClinicalTrialStatus status, int limit) {
        log.debug("findByStatus() | status={}, limit={}", status, limit);
        List<ClinicalTrialEntity> entities = repository.findByStatusOrderByDiscoveredAtDesc(status.name());
        List<ClinicalTrial> result = toLimitedDomainList(entities, limit);
        log.debug("findByStatus() | return={} trials", result.size());
        return result;
    }

    @Override
    public List<ClinicalTrial> findByPhase(ClinicalTrialPhase phase, int limit) {
        log.debug("findByPhase() | phase={}, limit={}", phase, limit);
        List<ClinicalTrialEntity> entities = repository.findByPhaseOrderByDiscoveredAtDesc(phase.name());
        List<ClinicalTrial> result = toLimitedDomainList(entities, limit);
        log.debug("findByPhase() | return={} trials", result.size());
        return result;
    }

    @Override
    public Optional<ClinicalTrial> findById(String trialId) {
        log.debug("findById() | trialId={}", trialId);
        Optional<ClinicalTrial> result = repository.findById(trialId).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public List<ClinicalTrial> findByKeyword(String keyword, int limit) {
        log.debug("findByKeyword() | keyword={}, limit={}", keyword, limit);
        List<ClinicalTrialEntity> entities = repository.findByKeyword(keyword);
        List<ClinicalTrial> result = toLimitedDomainList(entities, limit);
        log.debug("findByKeyword() | return={} trials", result.size());
        return result;
    }

    private List<ClinicalTrial> toLimitedDomainList(List<ClinicalTrialEntity> entities, int limit) {
        List<ClinicalTrial> result = new ArrayList<>();
        int count = 0;
        for (ClinicalTrialEntity entity : entities) {
            if (count >= limit) {
                break;
            }
            result.add(toDomain(entity));
            count++;
        }
        return result;
    }

    private ClinicalTrialEntity toEntity(ClinicalTrial trial) {
        ClinicalTrialEntity entity = new ClinicalTrialEntity();
        entity.setTrialId(trial.trialId());
        entity.setNctId(trial.nctId());
        entity.setTitle(truncate(trial.trialId(), trial.title(), "title", TITLE_MAX_LENGTH));
        entity.setSponsor(truncate(trial.trialId(), trial.sponsor(), "sponsor", SPONSOR_MAX_LENGTH));
        entity.setStatus(trial.status().name());
        entity.setPhase(trial.phase() != null ? trial.phase().name() : null);
        entity.setConditions(joinPipeDelimited(trial.conditions()));
        entity.setBriefSummary(trial.briefSummary());
        entity.setSourceUrl(truncate(trial.trialId(), trial.sourceUrl(), "sourceUrl", SOURCE_URL_MAX_LENGTH));
        entity.setStudyType(trial.studyType());
        entity.setStartDate(trial.startDate());
        entity.setDiscoveredAt(trial.discoveredAt());
        entity.setAiHealthcareKeywords(joinPipeDelimited(trial.aiHealthcareKeywords()));
        return entity;
    }

    /**
     * Clips a value to the database column's max length, logging a warning
     * when clipping actually occurs. Titles and sponsor names come from the
     * ClinicalTrials.gov API with no length guarantee.
     *
     * @param trialId   the owning trial's id, for the warning log
     * @param value     the value to clip; null passes through unchanged
     * @param fieldName the column name, for the warning log
     * @param maxLength the column's max length
     * @return the value, clipped to {@code maxLength} characters if needed
     */
    private String truncate(String trialId, String value, String fieldName, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.warn("truncate() | trial {} field '{}' is {} chars, exceeding column limit of {} — clipping",
                trialId, fieldName, value.length(), maxLength);
        return value.substring(0, maxLength);
    }

    private ClinicalTrial toDomain(ClinicalTrialEntity entity) {
        return new ClinicalTrial(
                entity.getTrialId(),
                entity.getNctId(),
                entity.getTitle(),
                entity.getSponsor(),
                ClinicalTrialStatus.valueOf(entity.getStatus()),
                entity.getPhase() != null ? ClinicalTrialPhase.valueOf(entity.getPhase()) : null,
                splitPipeDelimited(entity.getConditions()),
                entity.getBriefSummary(),
                entity.getSourceUrl(),
                entity.getStudyType(),
                entity.getStartDate(),
                entity.getDiscoveredAt(),
                splitPipeDelimited(entity.getAiHealthcareKeywords())
        );
    }

    private String joinPipeDelimited(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append("|");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private List<String> splitPipeDelimited(String pipeDelimited) {
        if (pipeDelimited == null || pipeDelimited.isBlank()) {
            return List.of();
        }
        return Arrays.asList(pipeDelimited.split("\\|"));
    }
}
