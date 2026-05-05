package com.wgblackmon.aihealthcare.infrastructure.research;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RetrievalQuery;
import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
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
 * Unit tests for {@link LegacyGoogleResearchAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
@ExtendWith(MockitoExtension.class)
class LegacyGoogleResearchAdapterTest {

    @Mock
    private ArticleIngestionPort articleIngestionPort;

    private LegacyGoogleResearchAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LegacyGoogleResearchAdapter(articleIngestionPort);
    }

    @Test
    void retrieve_delegatesToFetchAllByTopic() {
        when(articleIngestionPort.fetchAllByTopic("AI diagnostics")).thenReturn(Collections.emptyList());

        adapter.retrieve(new RetrievalQuery("AI diagnostics", "GOOGLE", 10));

        verify(articleIngestionPort).fetchAllByTopic("AI diagnostics");
    }

    @Test
    void retrieve_mapsArticleToRetrievedSource() {
        NewsArticle article = article("art-1", "AI in Radiology",
                URI.create("https://example.com/radiology"),
                "Body text about AI", Instant.parse("2026-01-01T00:00:00Z"));
        when(articleIngestionPort.fetchAllByTopic("radiology"))
                .thenReturn(List.of(article));

        List<RetrievedSource> result = adapter.retrieve(new RetrievalQuery("radiology", "GOOGLE", 10));

        assertThat(result).hasSize(1);
        RetrievedSource source = result.get(0);
        assertThat(source.sourceId()).isEqualTo("art-1");
        assertThat(source.title()).isEqualTo("AI in Radiology");
        assertThat(source.url()).isEqualTo("https://example.com/radiology");
        assertThat(source.snippet()).isEqualTo("Body text about AI");
        assertThat(source.engine()).isEqualTo("GOOGLE");
        assertThat(source.retrievedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void retrieve_nullPublishedAt_usesNow() {
        NewsArticle article = article("art-1", "Title", URI.create("https://x.com"), "", null);
        when(articleIngestionPort.fetchAllByTopic("topic")).thenReturn(List.of(article));

        Instant before = Instant.now();
        List<RetrievedSource> result = adapter.retrieve(new RetrievalQuery("topic", "GOOGLE", 10));
        Instant after = Instant.now();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).retrievedAt()).isBetween(before, after);
    }

    @Test
    void retrieve_respectsMaxResultsCap() {
        List<NewsArticle> articles = List.of(
                article("a1", "T1", URI.create("https://x.com/1"), "", null),
                article("a2", "T2", URI.create("https://x.com/2"), "", null),
                article("a3", "T3", URI.create("https://x.com/3"), "", null));
        when(articleIngestionPort.fetchAllByTopic("topic")).thenReturn(articles);

        List<RetrievedSource> result = adapter.retrieve(new RetrievalQuery("topic", "GOOGLE", 2));

        assertThat(result).hasSize(2);
    }

    @Test
    void retrieve_emptyIngestion_returnsEmptyList() {
        when(articleIngestionPort.fetchAllByTopic("empty-topic")).thenReturn(Collections.emptyList());

        List<RetrievedSource> result = adapter.retrieve(new RetrievalQuery("empty-topic", "GOOGLE", 20));

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private NewsArticle article(String id, String title, URI url, String body, Instant publishedAt) {
        return new NewsArticle(id, title, url, body, "test-topic",
                null, null, "Test Source", "INDUSTRY", 0.5, publishedAt);
    }
}
