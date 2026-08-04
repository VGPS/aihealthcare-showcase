package com.wgblackmon.aihealthcare.infrastructure.ingestion.feed;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.Topic;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.service.TopicSummaryGenerationService;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.ArticleRelevanceFilter;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceConfig.FeedTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RomeFeedHarvester}, {@link FeedSourceConfig},
 * {@link FeedSourceProperties}, and {@link Topic}.
 *
 * <p>Uses Mockito to stub {@link FeedSourceProperties} so no live network
 * calls are made.  Tests verify topicId propagation, mapping logic, tier
 * filtering behavior, and graceful failure isolation when a feed is unreachable.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-10
 * @updated 2026-04-10
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RomeFeedHarvesterTest {

    private static final Long TEST_TOPIC_ID = 1L;

    @Mock
    private FeedSourceProperties mockProperties;

    @Mock
    private ArticleStoragePort mockArticleStoragePort;

    @Mock
    private TopicSummaryGenerationService mockTopicSummaryService;

    @Mock
    private NewsTopicProperties mockNewsTopicProperties;

    private RomeFeedHarvester harvester;

    @BeforeEach
    void setUp() {
        when(mockProperties.toFeedSourceConfigs()).thenReturn(List.of(
                new FeedSourceConfig(TEST_TOPIC_ID, "Bad Feed",
                        "General AI Healthcare News",
                        "http://localhost:0/nonexistent-feed.rss",
                        FeedTier.INDUSTRY, 0.5, 10, java.util.List.of())
        ));

        harvester = new RomeFeedHarvester(mockProperties, new ArticleRelevanceFilter());
    }

    @Test
    @DisplayName("harvestAll() returns empty list when all feeds are unreachable")
    void harvestAll_whenAllFeedsUnreachable_returnsEmptyList() {
        List<NewsArticle> result = harvester.harvestAll();

        assertThat(result)
                .as("Should return empty list, not throw, when feeds are unreachable")
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("harvestAll() result is unmodifiable")
    void harvestAll_resultIsUnmodifiable() {
        List<NewsArticle> result = harvester.harvestAll();

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> result.add(null),
                "Result list from harvestAll() should be unmodifiable"
        );
    }

    @Test
    @DisplayName("FeedSourceConfig record carries topicId correctly")
    void feedSourceConfig_topicId_propagatedCorrectly() {
        FeedSourceConfig config = new FeedSourceConfig(
                42L,
                "PubMed AI Healthcare",
                "General AI Healthcare News",
                "https://pubmed.ncbi.nlm.nih.gov/rss/search/?term=artificial+intelligence+healthcare&format=rss",
                FeedTier.ACADEMIC,
                0.90,
                50,
                java.util.List.of()
        );

        assertThat(config.topicId()).isEqualTo(42L);
        assertThat(config.name()).isEqualTo("PubMed AI Healthcare");
        assertThat(config.tier()).isEqualTo(FeedTier.ACADEMIC);
        assertThat(config.baseWeight()).isEqualTo(0.90);
        assertThat(config.maxItems()).isEqualTo(50);
    }

    @Test
    @DisplayName("FeedSourceProperties.toFeedSourceConfigs() maps topicId into config records")
    void feedSourceProperties_toFeedSourceConfigs_mapsTopicIdCorrectly() {
        FeedSourceProperties props = new FeedSourceProperties();

        FeedSourceProperties.FeedEntry entry = new FeedSourceProperties.FeedEntry();
        entry.setTopicId(99L);
        entry.setName("Test Feed");
        entry.setUrl("https://example.com/feed.rss");
        entry.setTier(FeedTier.REGULATORY);
        entry.setBaseWeight(0.80);
        entry.setMaxItems(15);

        props.setSources(List.of(entry));

        List<FeedSourceConfig> configs = props.toFeedSourceConfigs();

        assertThat(configs).hasSize(1);
        FeedSourceConfig config = configs.get(0);
        assertThat(config.topicId()).isEqualTo(99L);
        assertThat(config.name()).isEqualTo("Test Feed");
        assertThat(config.tier()).isEqualTo(FeedTier.REGULATORY);
    }

    @Test
    @DisplayName("FeedTier enum covers all expected tiers")
    void feedTier_allValuesPresent() {
        assertThat(FeedTier.values())
                .containsExactlyInAnyOrder(FeedTier.ACADEMIC, FeedTier.REGULATORY,
                        FeedTier.INDUSTRY, FeedTier.COMPETITOR, FeedTier.HUGGINGFACE,
                        FeedTier.PERPLEXITY);
    }

    @Test
    @DisplayName("Topic record accessors return configured values")
    void topic_recordAccessors_returnCorrectValues() {
        Topic topic = new Topic(
                1L,
                "AI Healthcare",
                "ai-healthcare",
                "Focus on clinical AI, FDA approvals, and healthcare interoperability.",
                "Professional and concise",
                true
        );

        assertThat(topic.id()).isEqualTo(1L);
        assertThat(topic.name()).isEqualTo("AI Healthcare");
        assertThat(topic.slug()).isEqualTo("ai-healthcare");
        assertThat(topic.promptContext()).contains("FDA approvals");
        assertThat(topic.tone()).isEqualTo("Professional and concise");
        assertThat(topic.active()).isTrue();
    }

    @Test
    @DisplayName("Topic record with active=false is recognized as inactive")
    void topic_inactive_recognizedCorrectly() {
        Topic inactiveTopic = new Topic(
                2L, "AI Legal", "ai-legal",
                "Focus on AI regulation and legal frameworks.",
                "Formal", false
        );

        assertThat(inactiveTopic.active()).isFalse();
    }

    @Test
    @DisplayName("FeedHarvestScheduler constructs successfully with both ports")
    void feedHarvestScheduler_constructionWithBothPorts_doesNotThrow() {
        FeedHarvestScheduler scheduler = new FeedHarvestScheduler(harvester, mockArticleStoragePort, mockTopicSummaryService, mockNewsTopicProperties, null, null, null, null, null, null, null, null);

        assertThat(scheduler).isNotNull();
    }
}
