package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record representing a single knowledge gap — a topic
 * covered by recent articles but missing from the wiki.
 *
 * <p>Each gap links to the specific article IDs that cover the topic,
 * enabling the admin to review evidence before approving compilation.
 *
 * @param topic          the uncovered topic name
 * @param articleIds     article IDs that cover this topic but have no wiki page
 * @param recommendation what wiki page should be created to address this gap
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
public record GapItem(
        String topic,
        List<String> articleIds,
        String recommendation
) {

    /**
     * Compact canonical constructor — validates required fields and
     * creates defensive copies of all list fields.
     */
    public GapItem {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (articleIds == null) {
            throw new IllegalArgumentException("articleIds must not be null");
        }
        if (recommendation == null || recommendation.isBlank()) {
            throw new IllegalArgumentException("recommendation must not be blank");
        }
        articleIds = List.copyOf(articleIds);
    }
}
