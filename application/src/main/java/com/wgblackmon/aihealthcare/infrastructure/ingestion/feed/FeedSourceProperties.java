package com.wgblackmon.aihealthcare.infrastructure.ingestion.feed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Boot externalized configuration for all RSS/Atom feed sources.
 *
 * <p>Binds to the {@code aihealthcare.feeds} prefix in {@code application.yml}.
 * Each entry in {@code sources} maps directly to a {@link FeedSourceConfig}
 * record used by {@link RomeFeedHarvester}.
 *
 * <p>Example YAML:
 * <pre>
 * aihealthcare:
 *   feeds:
 *     sources:
 *       - topic-id: 1
 *         name: PubMed AI Healthcare
 *         url: https://pubmed.ncbi.nlm.nih.gov/rss/search/?term=artificial+intelligence+healthcare&amp;format=rss
 *         tier: ACADEMIC
 *         base-weight: 0.9
 *         max-items: 50
 * </pre>
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-04-10
 * @updated 2026-05-20
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "aihealthcare.feeds")
public class FeedSourceProperties {

    private List<FeedEntry> sources = new ArrayList<>();

    public List<FeedEntry> getSources() {
        log.debug("getSources() |");
        List<FeedEntry> result = sources;
        log.debug("getSources() | return={} configured feed sources", result.size());
        return result;
    }

    public void setSources(List<FeedEntry> sources) {
        log.debug("setSources() | sources.size={}", sources == null ? 0 : sources.size());
        this.sources = sources == null ? new ArrayList<>() : sources;
        log.debug("setSources() | return=void");
    }

    /**
     * Converts the mutable property entries into immutable {@link FeedSourceConfig} records.
     *
     * @return unmodifiable list of feed source configurations
     */
    public List<FeedSourceConfig> toFeedSourceConfigs() {
        log.debug("toFeedSourceConfigs() |");
        List<FeedSourceConfig> configs = new ArrayList<>();
        for (FeedEntry entry : sources) {
            configs.add(new FeedSourceConfig(
                    entry.getTopicId(),
                    entry.getName(),
                    entry.getTopic(),
                    entry.getUrl(),
                    entry.getTier(),
                    entry.getBaseWeight(),
                    entry.getMaxItems(),
                    java.util.List.copyOf(entry.getKeywords())
            ));
        }
        List<FeedSourceConfig> result = List.copyOf(configs);
        log.debug("toFeedSourceConfigs() | return={} configs", result.size());
        return result;
    }

    /**
     * Mutable JavaBean representation of a single feed entry in YAML.
     * Converted to the immutable {@link FeedSourceConfig} record at startup.
     */
    public static class FeedEntry {

        private Long topicId = 1L;
        private String name;
        private String topic;
        private String url;
        private FeedSourceConfig.FeedTier tier = FeedSourceConfig.FeedTier.INDUSTRY;
        private double baseWeight = 0.5;
        private int maxItems = 25;
        private java.util.List<String> keywords = new ArrayList<>();

        public Long getTopicId() { return topicId; }
        public void setTopicId(Long topicId) { this.topicId = topicId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public FeedSourceConfig.FeedTier getTier() { return tier; }
        public void setTier(FeedSourceConfig.FeedTier tier) { this.tier = tier; }

        public double getBaseWeight() { return baseWeight; }
        public void setBaseWeight(double baseWeight) { this.baseWeight = baseWeight; }

        public int getMaxItems() { return maxItems; }
        public void setMaxItems(int maxItems) { this.maxItems = maxItems; }

        public java.util.List<String> getKeywords() { return keywords; }
        public void setKeywords(java.util.List<String> keywords) {
            this.keywords = keywords == null ? new ArrayList<>() : keywords;
        }
    }
}
