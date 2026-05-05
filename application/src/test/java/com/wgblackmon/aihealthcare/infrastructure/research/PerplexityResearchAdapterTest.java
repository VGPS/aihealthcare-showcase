package com.wgblackmon.aihealthcare.infrastructure.research;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RetrievalQuery;
import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity.PerplexityHarvester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PerplexityResearchAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
@ExtendWith(MockitoExtension.class)
class PerplexityResearchAdapterTest {

    @Mock
    private PerplexityHarvester perplexityHarvester;

    private PerplexityResearchAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PerplexityResearchAdapter(perplexityHarvester);
    }

    @Test
    void retrieve_delegatesToHarvestArticles() {
        when(perplexityHarvester.harvestArticles("AI diagnostics")).thenReturn(Collections.emptyList());

        adapter.retrieve(new RetrievalQuery("AI diagnostics", "PERPLEXITY", 10));

        verify(perplexityHarvester).harvestArticles("AI diagnostics");
    }

    @Test
    void retrieve_harvesterReturnsEmpty_returnsEmptyList() {
        when(perplexityHarvester.harvestArticles("topic")).thenReturn(Collections.emptyList());

        List<RetrievedSource> result = adapter.retrieve(new RetrievalQuery("topic", "PERPLEXITY", 20));

        assertThat(result).isEmpty();
    }

    @Test
    void retrieve_mapsArticleToRetrievedSourceWithPerplexityEngine() {
        NewsArticle article = new NewsArticle(
                "px-1", "Perplexity Article", URI.create("https://perplexity.ai/result"),
                "Some snippet", "AI healthcare", null, null,
                "Perplexity", "PERPLEXITY", 0.8, Instant.parse("2026-03-01T00:00:00Z"));
        when(perplexityHarvester.harvestArticles("AI healthcare")).thenReturn(List.of(article));

        List<RetrievedSource> result = adapter.retrieve(new RetrievalQuery("AI healthcare", "PERPLEXITY", 10));

        assertThat(result).hasSize(1);
        RetrievedSource source = result.get(0);
        assertThat(source.sourceId()).isEqualTo("px-1");
        assertThat(source.engine()).isEqualTo("PERPLEXITY");
        assertThat(source.url()).isEqualTo("https://perplexity.ai/result");
    }

    @Test
    void retrieve_respectsMaxResultsCap() {
        List<NewsArticle> articles = List.of(
                article("p1"), article("p2"), article("p3"), article("p4"));
        when(perplexityHarvester.harvestArticles("topic")).thenReturn(articles);

        List<RetrievedSource> result = adapter.retrieve(new RetrievalQuery("topic", "PERPLEXITY", 2));

        assertThat(result).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private NewsArticle article(String id) {
        return new NewsArticle(id, "Title " + id, URI.create("https://x.com/" + id),
                "body", "topic", null, null, "src", "PERPLEXITY", 0.8, null);
    }
}
