package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /**
     * Returns aggregate score averages grouped by variant, ordered by average
     * overall score descending.  Each element is a nine-element {@code Object[]}:
     * <ol>
     *   <li>variantId (String)</li>
     *   <li>variantName (String)</li>
     *   <li>count (Long)</li>
     *   <li>avgOverall (Double)</li>
     *   <li>avgRelevance (Double)</li>
     *   <li>avgConciseness (Double)</li>
     *   <li>avgCompleteness (Double)</li>
     *   <li>avgToneMatch (Double)</li>
     *   <li>avgAttributionQuality (Double)</li>
     * </ol>
     *
     * @return list of per-variant aggregate score rows
     */
    @Query("SELECT e.variantId, e.variantName, COUNT(e), "
         + "AVG(e.overall), AVG(e.relevance), AVG(e.conciseness), "
         + "AVG(e.completeness), AVG(e.toneMatch), AVG(e.attributionQuality) "
         + "FROM EvaluationResultEntity e "
         + "GROUP BY e.variantId, e.variantName "
         + "ORDER BY AVG(e.overall) DESC")
    List<Object[]> getVariantAggregates();
}
