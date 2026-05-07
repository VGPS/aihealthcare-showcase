package com.wgblackmon.aihealthcare.infrastructure.research;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Externalized configuration for the scheduled research harvester.
 *
 * <p>Binds to the {@code aihealthcare.research.harvest} prefix in
 * {@code application.yml}.  Topics are a plain YAML list — add, remove, or
 * reorder entries without touching any Java code:
 *
 * <pre>
 * aihealthcare:
 *   research:
 *     harvest:
 *       cron: "0 0 6 * * *"
 *       max-sources-per-topic: 20
 *       topics:
 *         - "AI Healthcare Software Development"
 *         - "Healthcare Outsourcing"
 *         - "AI Government Policy"
 * </pre>
 *
 * <p>All fields have safe defaults so the application starts even if the
 * {@code harvest} block is absent from the config file.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-07
 * @updated 2026-05-07
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "aihealthcare.research.harvest")
public class ResearchHarvestProperties {

    /** Cron expression controlling harvest frequency. Default: daily at 06:00 UTC. */
    private String cron = "0 0 6 * * *";

    /** Maximum sources retrieved per topic per run. */
    private int maxSourcesPerTopic = 20;

    /**
     * List of research topics harvested on the scheduled cron.
     * Edit this list in {@code application.yml} — no code changes needed.
     */
    private List<String> topics = new ArrayList<>();

    public String getCron() {
        log.debug("getCron() | return={}", cron);
        return cron;
    }

    public void setCron(String cron) {
        log.debug("setCron() | cron={}", cron);
        this.cron = cron;
        log.debug("setCron() | return=void");
    }

    public int getMaxSourcesPerTopic() {
        log.debug("getMaxSourcesPerTopic() | return={}", maxSourcesPerTopic);
        return maxSourcesPerTopic;
    }

    public void setMaxSourcesPerTopic(int maxSourcesPerTopic) {
        log.debug("setMaxSourcesPerTopic() | maxSourcesPerTopic={}", maxSourcesPerTopic);
        this.maxSourcesPerTopic = maxSourcesPerTopic;
        log.debug("setMaxSourcesPerTopic() | return=void");
    }

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
