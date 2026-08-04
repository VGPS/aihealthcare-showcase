package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;

import java.util.List;
import java.util.Map;

/**
 * Outbound port for persisting and querying pipeline run events.
 *
 * <p>Implementations store {@link PipelineRunEvent} records in the persistence
 * layer.  Each event captures the outcome of a single pipeline step execution,
 * enabling durable run history that survives application restarts.
 *
 * <p>The {@code findLatestPerPipeline()} method supports the admin dashboard's
 * health-dot display by returning the most recent event for each distinct
 * pipeline identifier.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface PipelineRunEventPort {

    /**
     * Persists a pipeline run event.
     *
     * @param event the event to save
     */
    void save(PipelineRunEvent event);

    /**
     * Returns the most recent events across all pipelines, ordered by
     * started-at descending.
     *
     * @param limit maximum number of events to return
     * @return list of recent events; empty if none exist
     */
    List<PipelineRunEvent> findRecent(int limit);

    /**
     * Returns the most recent events for a specific pipeline, ordered by
     * started-at descending.
     *
     * @param pipelineId the pipeline identifier to filter on
     * @param limit      maximum number of events to return
     * @return list of events for the given pipeline; empty if none exist
     */
    List<PipelineRunEvent> findByPipelineId(String pipelineId, int limit);

    /**
     * Returns the latest event per unique pipeline identifier.
     *
     * <p>Used by the admin dashboard to render per-pipeline health indicators.
     * The map key is the pipeline ID and the value is the most recent event
     * for that pipeline.
     *
     * @return map of pipeline ID to latest event; empty if no events exist
     */
    Map<String, PipelineRunEvent> findLatestPerPipeline();
}
