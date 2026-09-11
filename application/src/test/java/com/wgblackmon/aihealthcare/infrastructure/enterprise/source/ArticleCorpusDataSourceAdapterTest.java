package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ArticleCorpusDataSourceAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-12
 */
class ArticleCorpusDataSourceAdapterTest {

    private ArticleIngestionPort articlePort;
    private DataJobLog jobLog;
    private ArticleCorpusDataSourceAdapter adapter;

    @BeforeEach
    void setUp() {
        articlePort = mock(ArticleIngestionPort.class);
        jobLog = mock(DataJobLog.class);
        adapter = new ArticleCorpusDataSourceAdapter(articlePort);
    }

    @Test
    void feedIdAndKind() {
        assertThat(adapter.feedId()).isEqualTo("articles");
        assertThat(adapter.kind()).isEqualTo(DataSourceKind.INTERNAL_CORPUS);
    }

    @Test
    void describeReturnsValidFeed() {
        DataFeed feed = adapter.describe();
        assertThat(feed.feedId()).isEqualTo("articles");
        assertThat(feed.label()).isEqualTo("Article Corpus");
        assertThat(feed.active()).isTrue();
        assertThat(feed.parameters()).hasSize(2);
        assertThat(feed.supportedFormats()).contains(ExportFormat.CSV, ExportFormat.JSON);
    }

    @Test
    void supportsMatchesFeedId() {
        DataRequest match = makeRequest("articles", Map.of(), null, 100);
        DataRequest noMatch = makeRequest("legislation", Map.of(), null, 100);
        assertThat(adapter.supports(match)).isTrue();
        assertThat(adapter.supports(noMatch)).isFalse();
    }

    @Test
    void fetchRecentArticlesDefaultPath() {
        when(articlePort.fetchRecentArticles(30)).thenReturn(List.of(sampleArticle("a1"), sampleArticle("a2")));

        DataSet result = adapter.fetch(makeRequest("articles", Map.of(), null, 100), jobLog);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.columns()).hasSize(5);
        assertThat(result.rows().get(0).get(0)).isEqualTo("Title a1");
        assertThat(result.rows().get(0).get(1)).isEqualTo("https://example.com/a1");
        assertThat(result.truncatedAtRows()).isZero();
        verify(jobLog).phase(eq("FETCH_START"), anyString());
        verify(jobLog).phase(eq("FETCH_END"), anyString());
    }

    @Test
    void fetchByKeywordParam() {
        when(articlePort.fetchArticles("FDA", 50)).thenReturn(List.of(sampleArticle("a1")));

        DataSet result = adapter.fetch(makeRequest("articles", Map.of("keyword", "FDA"), null, 50), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(articlePort).fetchArticles("FDA", 50);
    }

    @Test
    void fetchByQueryPlanKeywords() {
        DataQueryPlan plan = new DataQueryPlan("articles", List.of("telehealth"), null, null, List.of(), List.of(), null, 100);
        when(articlePort.fetchArticles("telehealth", 100)).thenReturn(List.of(sampleArticle("a1")));

        DataSet result = adapter.fetch(makeRequest("articles", Map.of(), plan, 100), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(articlePort).fetchArticles("telehealth", 100);
    }

    @Test
    void fetchByQueryPlanDateRange() {
        DataQueryPlan plan = new DataQueryPlan("articles", List.of(),
                java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 9, 7),
                List.of(), List.of(), null, 100);
        when(articlePort.fetchArticlesByDateRange(any(), any())).thenReturn(List.of(sampleArticle("a1")));

        DataSet result = adapter.fetch(makeRequest("articles", Map.of(), plan, 100), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(articlePort).fetchArticlesByDateRange(any(), any());
    }

    @Test
    void rowLimitTruncatesAndSetsTruncatedCount() {
        List<NewsArticle> articles = List.of(
                sampleArticle("a1"), sampleArticle("a2"), sampleArticle("a3"));
        when(articlePort.fetchRecentArticles(30)).thenReturn(articles);

        DataSet result = adapter.fetch(makeRequest("articles", Map.of(), null, 2), jobLog);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.truncatedAtRows()).isEqualTo(3);
    }

    @Test
    void emptyResultReturnsEmptyDataSet() {
        when(articlePort.fetchRecentArticles(30)).thenReturn(Collections.emptyList());

        DataSet result = adapter.fetch(makeRequest("articles", Map.of(), null, 100), jobLog);

        assertThat(result.rows()).isEmpty();
        assertThat(result.truncatedAtRows()).isZero();
    }

    @Test
    void rowWidthMatchesColumnCount() {
        when(articlePort.fetchRecentArticles(30)).thenReturn(List.of(sampleArticle("a1")));

        DataSet result = adapter.fetch(makeRequest("articles", Map.of(), null, 100), jobLog);

        assertThat(result.rows().get(0)).hasSize(result.columns().size());
    }

    private NewsArticle sampleArticle(String id) {
        return new NewsArticle(
                id, "Title " + id, URI.create("https://example.com/" + id),
                "body", "AI Healthcare", null, null,
                "TestSource", "INDUSTRY", 0.5,
                Instant.parse("2026-09-01T00:00:00Z")
        );
    }

    private DataRequest makeRequest(String feedId, Map<String, String> params, DataQueryPlan plan, int rowLimit) {
        return new DataRequest(
                "job-1", "user@test.com", null,
                DataJobMode.PULL, feedId, null, null,
                params, ExportFormat.CSV, rowLimit,
                null, plan, Instant.now(), null
        );
    }
}
