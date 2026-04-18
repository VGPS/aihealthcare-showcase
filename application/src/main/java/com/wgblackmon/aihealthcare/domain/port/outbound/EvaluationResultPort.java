package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.ComparisonResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationResult;

import java.util.List;

/**
 * Outbound port — persist and retrieve prompt evaluation and comparison results.
 *
 * <p>Implementations live in {@code infrastructure/persistence}.  Evaluation
 * results are write-once, read-many artifacts — they are never updated after
 * initial persistence.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public interface EvaluationResultPort {

    /**
     * Persist an evaluation result.
     *
     * @param result the evaluation result to save
     */
    void save(EvaluationResult result);

    /**
     * Retrieve an evaluation result by its unique identifier.
     *
     * @param evaluationId the evaluation identifier
     * @return the matching result
     * @throws com.wgblackmon.aihealthcare.domain.exception.EvaluationNotFoundException
     *         if no result exists for the given ID
     */
    EvaluationResult findByEvaluationId(String evaluationId);

    /**
     * Retrieve all stored evaluation results.
     *
     * @return all results, or an empty list if none exist
     */
    List<EvaluationResult> findAll();

    /**
     * Retrieve all evaluation results for a specific prompt variant.
     *
     * @param variantId the variant identifier to filter by
     * @return matching results, or an empty list if none exist
     */
    List<EvaluationResult> findByVariantId(String variantId);

    /**
     * Persist a comparison result and its associated evaluation results.
     *
     * @param comparison the comparison result to save
     */
    void saveComparison(ComparisonResult comparison);

    /**
     * Retrieve a comparison result by its unique identifier.
     *
     * @param comparisonId the comparison identifier
     * @return the matching comparison with its linked evaluation results
     * @throws com.wgblackmon.aihealthcare.domain.exception.EvaluationNotFoundException
     *         if no comparison exists for the given ID
     */
    ComparisonResult findComparisonById(String comparisonId);

    /**
     * Retrieve all stored comparison results.
     *
     * @return all comparisons, or an empty list if none exist
     */
    List<ComparisonResult> findAllComparisons();
}
