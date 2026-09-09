package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiSynthesisDataSourceAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class AiSynthesisDataSourceAdapterTest {

    private ConductAiSearchUseCase aiSearchUseCase;
    private DataJobLog jobLog;
    private AiSynthesisDataSourceAdapter adapter;

    @BeforeEach
    void setUp() {
        aiSearchUseCase = mock(ConductAiSearchUseCase.class);
        jobLog = mock(DataJobLog.class);
        adapter = new AiSynthesisDataSourceAdapter(aiSearchUseCase);
    }

    @Test
    void feedIdAndKind() {
        assertThat(adapter.feedId()).isEqualTo("ai-synthesis");
        assertThat(adapter.kind()).isEqualTo(DataSourceKind.LLM_SYNTHESIS);
    }

    @Test
    void describeReturnsValidFeed() {
        DataFeed feed = adapter.describe();
        assertThat(feed.feedId()).isEqualTo("ai-synthesis");
        assertThat(feed.label()).isEqualTo("AI Market Brief");
        assertThat(feed.active()).isTrue();
        assertThat(feed.parameters()).hasSize(1);
        assertThat(feed.parameters().get(0).required()).isTrue();
    }

    @Test
    void supportsMatchesFeedId() {
        DataRequest match = makeRequest("ai-synthesis", Map.of("topic", "telehealth"), null, 20);
        DataRequest noMatch = makeRequest("articles", Map.of(), null, 20);
        assertThat(adapter.supports(match)).isTrue();
        assertThat(adapter.supports(noMatch)).isFalse();
    }

    @Test
    void fetchDelegatesTopicToAiSearch() {
        AiSearchResult searchResult = new AiSearchResult(
                "search-1", "telehealth",
                List.of(sampleArticle("a1")),
                List.of(new AiSearchSynthesis("Claude", "Telehealth is growing.",
                        List.of("Finding 1"), Instant.now())),
                List.of(), Instant.now()
        );
        when(aiSearchUseCase.search("telehealth", 20)).thenReturn(searchResult);

        DataSet result = adapter.fetch(makeRequest("ai-synthesis", Map.of("topic", "telehealth"), null, 20), jobLog);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.narrative()).contains("Telehealth is growing.");
        assertThat(result.narrative()).contains("Finding 1");
        assertThat(result.citations()).hasSize(1);
        verify(aiSearchUseCase).search("telehealth", 20);
    }

    @Test
    void fetchByQueryPlanKeywords() {
        DataQueryPlan plan = new DataQueryPlan("ai-synthesis", List.of("FDA", "AI"), null, null,
                List.of(), List.of(), null, 20);
        AiSearchResult searchResult = new AiSearchResult(
                "search-1", "FDA AI",
                List.of(sampleArticle("a1")),
                List.of(new AiSearchSynthesis("Claude", "FDA AI regulation.",
                        List.of(), Instant.now())),
                List.of(), Instant.now()
        );
        when(aiSearchUseCase.search("FDA AI", 20)).thenReturn(searchResult);

        DataSet result = adapter.fetch(makeRequest("ai-synthesis", Map.of(), plan, 20), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(aiSearchUseCase).search("FDA AI", 20);
    }

    @Test
    void narrativeContainsMultipleSyntheses() {
        AiSearchResult searchResult = new AiSearchResult(
                "search-1", "topic",
                List.of(sampleArticle("a1")),
                List.of(
                        new AiSearchSynthesis("Claude", "Claude says.", List.of(), Instant.now()),
                        new AiSearchSynthesis("GPT", "GPT says.", List.of("Key point"), Instant.now())
                ),
                List.of(), Instant.now()
        );
        when(aiSearchUseCase.search("topic", 20)).thenReturn(searchResult);

        DataSet result = adapter.fetch(makeRequest("ai-synthesis", Map.of("topic", "topic"), null, 20), jobLog);

        assertThat(result.narrative()).contains("## Claude");
        assertThat(result.narrative()).contains("## GPT");
        assertThat(result.narrative()).contains("Key point");
    }

    @Test
    void rowLimitTruncatesArticles() {
        AiSearchResult searchResult = new AiSearchResult(
                "search-1", "topic",
                List.of(sampleArticle("a1"), sampleArticle("a2"), sampleArticle("a3")),
                List.of(new AiSearchSynthesis("Claude", "Summary.", List.of(), Instant.now())),
                List.of(), Instant.now()
        );
        when(aiSearchUseCase.search("topic", 2)).thenReturn(searchResult);

        DataSet result = adapter.fetch(makeRequest("ai-synthesis", Map.of("topic", "topic"), null, 2), jobLog);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.truncatedAtRows()).isEqualTo(3);
        assertThat(result.citations()).hasSize(2);
    }

    @Test
    void emptySearchResultReturnsEmptyDataSet() {
        AiSearchResult searchResult = new AiSearchResult(
                "search-1", "topic",
                Collections.emptyList(),
                Collections.emptyList(),
                List.of("Claude"), Instant.now()
        );
        when(aiSearchUseCase.search("AI in healthcare", 20)).thenReturn(searchResult);

        DataSet result = adapter.fetch(makeRequest("ai-synthesis", Map.of(), null, 20), jobLog);

        assertThat(result.rows()).isEmpty();
        assertThat(result.narrative()).isEmpty();
        assertThat(result.truncatedAtRows()).isZero();
    }

    @Test
    void rowWidthMatchesColumnCount() {
        AiSearchResult searchResult = new AiSearchResult(
                "search-1", "topic",
                List.of(sampleArticle("a1")),
                List.of(new AiSearchSynthesis("Claude", "Summary.", List.of(), Instant.now())),
                List.of(), Instant.now()
        );
        when(aiSearchUseCase.search("topic", 20)).thenReturn(searchResult);

        DataSet result = adapter.fetch(makeRequest("ai-synthesis", Map.of("topic", "topic"), null, 20), jobLog);

        assertThat(result.rows().get(0)).hasSize(result.columns().size());
    }

    @Test
    void defaultQueryFallback() {
        AiSearchResult searchResult = new AiSearchResult(
                "search-1", "AI in healthcare",
                Collections.emptyList(), Collections.emptyList(),
                List.of(), Instant.now()
        );
        when(aiSearchUseCase.search("AI in healthcare", 20)).thenReturn(searchResult);

        adapter.fetch(makeRequest("ai-synthesis", Map.of(), null, 20), jobLog);

        verify(aiSearchUseCase).search("AI in healthcare", 20);
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
                null, plan, Instant.now()
        );
    }
}
