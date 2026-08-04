package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Spring Data JPA repository for {@link PipelineRunEventEntity}.
 *
 * <p>Provides query methods for retrieving pipeline run events ordered by
 * recency.  The {@code findLatestPerPipeline()} query uses a correlated
 * subquery to return only the most recent event for each distinct pipeline ID.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface PipelineRunEventRepository extends JpaRepository<PipelineRunEventEntity, Long> {

    /**
     * Returns recent events across all pipelines, ordered by started-at descending.
     * The caller controls the result size via the {@link Pageable} parameter.
     *
     * @param pageable pagination and size constraint
     * @return list of event entities, newest first
     */
    List<PipelineRunEventEntity> findAllByOrderByStartedAtDesc(Pageable pageable);

    /**
     * Returns recent events for a specific pipeline, ordered by started-at descending.
     * The caller controls the result size via the {@link Pageable} parameter.
     *
     * @param pipelineId the pipeline identifier to filter on
     * @param pageable   pagination and size constraint
     * @return list of event entities for the given pipeline, newest first
     */
    List<PipelineRunEventEntity> findByPipelineIdOrderByStartedAtDesc(String pipelineId, Pageable pageable);

    /**
     * Returns the latest event per unique pipeline ID using a correlated subquery.
     *
     * @return list of event entities, one per pipeline
     */
    @Query("SELECT e FROM PipelineRunEventEntity e WHERE e.startedAt = "
         + "(SELECT MAX(e2.startedAt) FROM PipelineRunEventEntity e2 "
         + "WHERE e2.pipelineId = e.pipelineId)")
    List<PipelineRunEventEntity> findLatestPerPipeline();
}
