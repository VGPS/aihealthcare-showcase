package com.wgblackmon.aihealthcare.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Externalized configuration for the news listing topic headers.
 *
 * <p>Binds to the {@code aihealthcare.news} prefix in {@code application.yml}.
 * The ordered list of topic names controls which section headers appear on
 * {@code GET /dashboard/news} and in what order.  Each topic name must match
 * a feed source {@code name} so that harvested articles group under the
 * correct header.
 *
 * <pre>
 * aihealthcare:
 *   news:
 *     topics:
 *       - "General AI Healthcare News"
 *       - "AI Healthcare Software Development"
 *       - "Beckers Hospital Review"
 * </pre>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-15
 * @updated 2026-05-15
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "aihealthcare.news")
public class NewsTopicProperties {

    /**
     * Ordered list of topic names displayed as section headers on the news listing page.
     * Edit this list in {@code application.yml} — no code changes needed.
     */
    private List<String> topics = new ArrayList<>();

    public List<String> getTopics() {
        log.debug("getTopics() | return={} topics", topics.size());
        return topics;
    }

    public void setTopics(List<String> topics) {
        log.debug("setTopics() | topics.size={}", topics == null ? 0 : topics.size());
        this.topics = topics == null ? new ArrayList<>() : topics;
        log.debug("setTopics() | return=void");
    }
}
