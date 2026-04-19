package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link EvaluationResultEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public interface EvaluationResultRepository extends JpaRepository<EvaluationResultEntity, String> {

    /**
     * Finds all evaluation results for a specific prompt variant.
     *
     * @param variantId the variant identifier to filter by
     * @return matching entities, or an empty list
     */
    List<EvaluationResultEntity> findByVariantId(String variantId);

    /**
     * Finds all evaluation results linked to a specific comparison.
     *
     * @param comparisonId the comparison identifier
     * @return matching entities, or an empty list
     */
    List<EvaluationResultEntity> findByComparisonId(String comparisonId);
}
