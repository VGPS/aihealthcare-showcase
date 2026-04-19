package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.exception.EvaluationNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.ComparisonResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationScore;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.port.outbound.EvaluationResultPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JPA-backed implementation of {@link EvaluationResultPort}.
 *
 * <p>Persists and retrieves {@link EvaluationResult} and {@link ComparisonResult}
 * domain records.  Article ID lists are serialized as pipe-delimited strings
 * for storage in a single TEXT column, avoiding a join table for a simple
 * list of strings.
 *
 * <p>Comparisons are reassembled by querying all {@link EvaluationResultEntity}
 * rows whose {@code comparisonId} matches the comparison being loaded.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
@Slf4j
@Component
public class EvaluationResultAdapter implements EvaluationResultPort {

    private static final String ID_DELIMITER = "|";

    private final EvaluationResultRepository  evalRepository;
    private final ComparisonResultRepository  compRepository;

    public EvaluationResultAdapter(EvaluationResultRepository evalRepository,
                                   ComparisonResultRepository compRepository) {
        log.debug("EvaluationResultAdapter() | evalRepository={}, compRepository={}",
                  evalRepository.getClass().getSimpleName(),
                  compRepository.getClass().getSimpleName());
        this.evalRepository = evalRepository;
        this.compRepository = compRepository;
    }

    // -------------------------------------------------------------------------
    // Evaluation CRUD
    // -------------------------------------------------------------------------

    @Override
    public void save(EvaluationResult result) {
        log.debug("save() | evaluationId={}", result.evaluationId());
        evalRepository.save(toEntity(result, null));
        log.debug("save() | return=void");
    }

    @Override
    public EvaluationResult findByEvaluationId(String evaluationId) {
        log.debug("findByEvaluationId() | evaluationId={}", evaluationId);
        EvaluationResultEntity entity = evalRepository.findById(evaluationId)
                .orElseThrow(() -> new EvaluationNotFoundException(evaluationId));
        EvaluationResult result = toDomain(entity);
        log.debug("findByEvaluationId() | return={}", result.evaluationId());
        return result;
    }

    @Override
    public List<EvaluationResult> findAll() {
        log.debug("findAll() | (no args)");
        List<EvaluationResultEntity> entities = evalRepository.findAll();
        List<EvaluationResult> result = new ArrayList<>();
        for (EvaluationResultEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findAll() | return={} evaluations", result.size());
        return result;
    }

    @Override
    public List<EvaluationResult> findByVariantId(String variantId) {
        log.debug("findByVariantId() | variantId={}", variantId);
        List<EvaluationResultEntity> entities = evalRepository.findByVariantId(variantId);
        List<EvaluationResult> result = new ArrayList<>();
        for (EvaluationResultEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findByVariantId() | return={} evaluations", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Comparison CRUD
    // -------------------------------------------------------------------------

    @Override
    public void saveComparison(ComparisonResult comparison) {
        log.debug("saveComparison() | comparisonId={}", comparison.comparisonId());

        // Save the comparison header
        ComparisonResultEntity compEntity = new ComparisonResultEntity();
        compEntity.setComparisonId(comparison.comparisonId());
        compEntity.setArticleIdsJoined(joinIds(comparison.articleIds()));
        compEntity.setTopic(comparison.topic());
        compEntity.setTone(comparison.tone().name());
        compEntity.setComparedAt(comparison.comparedAt());
        compRepository.save(compEntity);

        // Save each evaluation result linked to this comparison
        for (EvaluationResult evalResult : comparison.results()) {
            evalRepository.save(toEntity(evalResult, comparison.comparisonId()));
        }

        log.debug("saveComparison() | return=void");
    }

    @Override
    public ComparisonResult findComparisonById(String comparisonId) {
        log.debug("findComparisonById() | comparisonId={}", comparisonId);
        ComparisonResultEntity compEntity = compRepository.findById(comparisonId)
                .orElseThrow(() -> new EvaluationNotFoundException(comparisonId));

        List<EvaluationResultEntity> evalEntities = evalRepository.findByComparisonId(comparisonId);
        List<EvaluationResult> results = new ArrayList<>();
        for (EvaluationResultEntity entity : evalEntities) {
            results.add(toDomain(entity));
        }

        ComparisonResult result = new ComparisonResult(
                compEntity.getComparisonId(),
                splitIds(compEntity.getArticleIdsJoined()),
                compEntity.getTopic(),
                NewsletterTone.valueOf(compEntity.getTone()),
                results,
                compEntity.getComparedAt()
        );
        log.debug("findComparisonById() | return={}", result.comparisonId());
        return result;
    }

    @Override
    public List<ComparisonResult> findAllComparisons() {
        log.debug("findAllComparisons() | (no args)");
        List<ComparisonResultEntity> compEntities = compRepository.findAll();
        List<ComparisonResult> result = new ArrayList<>();
        for (ComparisonResultEntity compEntity : compEntities) {
            List<EvaluationResultEntity> evalEntities =
                    evalRepository.findByComparisonId(compEntity.getComparisonId());
            List<EvaluationResult> evalResults = new ArrayList<>();
            for (EvaluationResultEntity entity : evalEntities) {
                evalResults.add(toDomain(entity));
            }
            // Skip comparisons whose linked evaluations were deleted
            if (evalResults.size() < 2) {
                log.warn("findAllComparisons() | Comparison {} has fewer than 2 evaluations, skipping",
                         compEntity.getComparisonId());
                continue;
            }
            result.add(new ComparisonResult(
                    compEntity.getComparisonId(),
                    splitIds(compEntity.getArticleIdsJoined()),
                    compEntity.getTopic(),
                    NewsletterTone.valueOf(compEntity.getTone()),
                    evalResults,
                    compEntity.getComparedAt()
            ));
        }
        log.debug("findAllComparisons() | return={} comparisons", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Entity ↔ Domain mapping
    // -------------------------------------------------------------------------

    private EvaluationResultEntity toEntity(EvaluationResult result, String comparisonId) {
        log.debug("toEntity() | evaluationId={}", result.evaluationId());
        EvaluationResultEntity entity = new EvaluationResultEntity();

        entity.setEvaluationId(result.evaluationId());
        entity.setVariantId(result.variantId());
        entity.setVariantName(result.variantName());
        entity.setArticleIdsJoined(joinIds(result.articleIds()));
        entity.setTopic(result.topic());
        entity.setTone(result.tone().name());

        // Section fields
        NewsletterSection section = result.section();
        entity.setSectionId(section.sectionId());
        entity.setSectionType(section.sectionType().name());
        entity.setHeadline(section.headline());
        entity.setSummary(section.summary());
        entity.setSectionArticleIdsJoined(joinIds(section.articleIds()));

        // Score fields
        EvaluationScore score = result.score();
        entity.setRelevance(score.relevance());
        entity.setConciseness(score.conciseness());
        entity.setAttributionQuality(score.attributionQuality());
        entity.setToneMatch(score.toneMatch());
        entity.setCompleteness(score.completeness());
        entity.setOverall(score.overall());
        entity.setScoringNotes(score.scoringNotes());

        // Metadata
        entity.setComparisonId(comparisonId);
        entity.setEvaluatedAt(result.evaluatedAt());

        log.debug("toEntity() | return={}", entity.getEvaluationId());
        return entity;
    }

    private EvaluationResult toDomain(EvaluationResultEntity entity) {
        log.debug("toDomain() | evaluationId={}", entity.getEvaluationId());

        NewsletterSection section = new NewsletterSection(
                entity.getSectionId(),
                SectionType.valueOf(entity.getSectionType()),
                entity.getTopic(),
                entity.getHeadline(),
                entity.getSummary(),
                splitIds(entity.getSectionArticleIdsJoined())
        );

        EvaluationScore score = new EvaluationScore(
                entity.getRelevance(),
                entity.getConciseness(),
                entity.getAttributionQuality(),
                entity.getToneMatch(),
                entity.getCompleteness(),
                entity.getOverall(),
                entity.getScoringNotes()
        );

        EvaluationResult result = new EvaluationResult(
                entity.getEvaluationId(),
                entity.getVariantId(),
                entity.getVariantName(),
                splitIds(entity.getArticleIdsJoined()),
                entity.getTopic(),
                NewsletterTone.valueOf(entity.getTone()),
                section,
                score,
                entity.getEvaluatedAt()
        );

        log.debug("toDomain() | return={}", result.evaluationId());
        return result;
    }

    // -------------------------------------------------------------------------
    // ID list serialization
    // -------------------------------------------------------------------------

    private String joinIds(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(ID_DELIMITER);
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private List<String> splitIds(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        String[] parts = joined.split("\\|");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return result;
    }
}
