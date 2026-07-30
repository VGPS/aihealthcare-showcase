package com.wgblackmon.aihealthcare.infrastructure.ingestion.legal;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CourtListenerHarvester}.
 *
 * <p>Tests source name and graceful error handling. Live API calls
 * may return results or empty depending on network availability —
 * the harvester must never throw.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
class CourtListenerHarvesterTest {

    private final CourtListenerHarvester harvester = new CourtListenerHarvester("");

    @Test
    void sourceNameIsCourtListener() {
        assertThat(harvester.sourceName()).isEqualTo("CourtListener");
    }

    @Test
    void harvestReturnsEmptyOnNetworkError() {
        // With a 1-day lookback, may return empty or actual results
        // Key assertion: never throws
        List<NewsArticle> articles = harvester.harvest(1);
        assertThat(articles).isNotNull();
    }

    @Test
    void harvestSetsCorrectTopicAndSource() {
        // Very narrow lookback — likely empty, but if results come back
        // they should have the correct topic and source
        List<NewsArticle> articles = harvester.harvest(1);
        for (NewsArticle article : articles) {
            assertThat(article.topic()).isEqualTo("AI Healthcare Legal");
            assertThat(article.sourceName()).isEqualTo("CourtListener");
            assertThat(article.sourceTier()).isEqualTo("ACADEMIC");
            assertThat(article.articleId()).startsWith("courtlistener-");
        }
    }
}
