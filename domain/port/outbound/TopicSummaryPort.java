package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.TopicSummary;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and retrieving pre-generated topic summaries.
 *
 * <p>Implementations live in {@code infrastructure/persistence} and back onto
 * a JPA repository.  The {@code save} method uses upsert semantics — calling
 * it with an existing topic overwrites the previous summary.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-21
 * @updated 2026-05-21
 */
public interface TopicSummaryPort {

    /**
     * Persist a topic summary.  If a summary already exists for the same
     * topic, it is overwritten (upsert).
     *
     * @param summary the topic summary to save; must not be null
     */
    void save(TopicSummary summary);

    /**
     * Retrieve the most recent summary for the given topic.
     *
     * @param topic topic name to look up
     * @return the summary if one exists, or empty
     */
    Optional<TopicSummary> findByTopic(String topic);

    /**
     * Retrieve all persisted topic summaries.
     *
     * @return an unmodifiable list of all summaries; may be empty
     */
    List<TopicSummary> findAll();
}
