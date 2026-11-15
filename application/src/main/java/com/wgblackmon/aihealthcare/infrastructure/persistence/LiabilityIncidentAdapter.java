package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.HarmType;
import com.wgblackmon.aihealthcare.domain.model.IncidentStatus;
import com.wgblackmon.aihealthcare.domain.model.IncidentType;
import com.wgblackmon.aihealthcare.domain.model.LiabilityIncident;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.LiabilityIncidentPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA-backed implementation of {@link LiabilityIncidentPort}.
 *
 * <p>Enum fields are stored as VARCHAR strings using {@code name()} on save and
 * {@code valueOf()} on load — no {@code @Enumerated} annotation, per project convention.
 * {@link #findRelatedArticles} delegates to {@link NewsArticleRepository} to surface
 * existing harvested articles by defendant keyword — zero LLM cost.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-16
 * @updated 2026-11-16
 */
@Slf4j
@Component
public class LiabilityIncidentAdapter implements LiabilityIncidentPort {

    private static final int TITLE_MAX       = 500;
    private static final int PLAINTIFF_MAX   = 500;
    private static final int DEFENDANT_MAX   = 500;
    private static final int THEORY_MAX      = 200;
    private static final int COURT_MAX       = 200;
    private static final int CASE_NUM_MAX    = 100;
    private static final int OUTCOME_MAX     = 1000;
    private static final int SOURCE_URL_MAX  = 2000;

    private final LiabilityIncidentRepository repository;
    private final NewsArticleRepository       articleRepository;

    public LiabilityIncidentAdapter(LiabilityIncidentRepository repository,
                                    NewsArticleRepository articleRepository) {
        log.debug("LiabilityIncidentAdapter() | repository={}, articleRepository={}",
                  repository.getClass().getSimpleName(),
                  articleRepository.getClass().getSimpleName());
        this.repository        = repository;
        this.articleRepository = articleRepository;
    }

    @Override
    public void save(LiabilityIncident incident) {
        log.debug("save() | incidentId={}", incident.incidentId());
        repository.save(toEntity(incident));
        log.debug("save() | return=void");
    }

    @Override
    public List<LiabilityIncident> findAll() {
        log.debug("findAll()");
        List<LiabilityIncident> result = repository
                .findAllByOrderByFilingDateDescCreatedAtDesc()
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        log.debug("findAll() | return={}", result.size());
        return result;
    }

    @Override
    public LiabilityIncident findById(String incidentId) {
        log.debug("findById() | incidentId={}", incidentId);
        LiabilityIncident result = repository.findById(incidentId)
                .map(this::toDomain)
                .orElse(null);
        log.debug("findById() | return={}", result);
        return result;
    }

    @Override
    public void deleteById(String incidentId) {
        log.debug("deleteById() | incidentId={}", incidentId);
        repository.deleteById(incidentId);
        log.debug("deleteById() | return=void");
    }

    @Override
    public List<NewsArticle> findRelatedArticles(String keyword, int limit) {
        log.debug("findRelatedArticles() | keyword={}, limit={}", keyword, limit);
        List<NewsArticle> result = articleRepository
                .findByTitleContainingIgnoreCase(keyword)
                .stream()
                .limit(limit)
                .map(this::toNewsArticleDomain)
                .collect(Collectors.toList());
        log.debug("findRelatedArticles() | return={}", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private LiabilityIncidentEntity toEntity(LiabilityIncident i) {
        LiabilityIncidentEntity e = new LiabilityIncidentEntity();
        e.setIncidentId(i.incidentId());
        e.setIncidentType(i.incidentType().name());
        e.setHarmType(i.harmType().name());
        e.setStatus(i.status().name());
        e.setTitle(truncate(i.title(), TITLE_MAX));
        e.setPlaintiff(truncate(i.plaintiff(), PLAINTIFF_MAX));
        e.setDefendant(truncate(i.defendant(), DEFENDANT_MAX));
        e.setLegalTheory(truncate(i.legalTheory(), THEORY_MAX));
        e.setCourt(truncate(i.court(), COURT_MAX));
        e.setCaseNumber(truncate(i.caseNumber(), CASE_NUM_MAX));
        e.setHarmDate(i.harmDate());
        e.setFilingDate(i.filingDate());
        e.setOutcome(truncate(i.outcome(), OUTCOME_MAX));
        e.setSourceUrl(truncate(i.sourceUrl(), SOURCE_URL_MAX));
        e.setNotes(i.notes());
        e.setCreatedAt(i.createdAt());
        e.setUpdatedAt(i.updatedAt());
        return e;
    }

    private LiabilityIncident toDomain(LiabilityIncidentEntity e) {
        return new LiabilityIncident(
                e.getIncidentId(),
                IncidentType.valueOf(e.getIncidentType()),
                HarmType.valueOf(e.getHarmType()),
                IncidentStatus.valueOf(e.getStatus()),
                e.getTitle(),
                e.getPlaintiff(),
                e.getDefendant(),
                e.getLegalTheory(),
                e.getCourt(),
                e.getCaseNumber(),
                e.getHarmDate(),
                e.getFilingDate(),
                e.getOutcome(),
                e.getSourceUrl(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private NewsArticle toNewsArticleDomain(NewsArticleEntity e) {
        URI url = null;
        try {
            if (e.getUrl() != null) {
                url = URI.create(e.getUrl());
            }
        } catch (IllegalArgumentException ex) {
            log.warn("toNewsArticleDomain() | invalid URI skipped for article={}", e.getArticleId());
        }
        return new NewsArticle(
                e.getArticleId(),
                e.getTitle(),
                url,
                e.getBodyText(),
                e.getTopic(),
                e.getAuthor(),
                e.getTopicId(),
                e.getSourceName(),
                e.getSourceTier(),
                e.getSourceWeight(),
                e.getPublishedAt()
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength) {
            log.warn("truncate() | value truncated to {} chars", maxLength);
            return value.substring(0, maxLength);
        }
        return value;
    }
}
