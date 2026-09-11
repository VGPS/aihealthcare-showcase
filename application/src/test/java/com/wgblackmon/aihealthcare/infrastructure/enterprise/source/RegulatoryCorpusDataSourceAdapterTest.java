package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RegulatoryCorpusDataSourceAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-12
 */
class RegulatoryCorpusDataSourceAdapterTest {

    private RegulatoryEventPort regulatoryPort;
    private DataJobLog jobLog;
    private RegulatoryCorpusDataSourceAdapter adapter;

    @BeforeEach
    void setUp() {
        regulatoryPort = mock(RegulatoryEventPort.class);
        jobLog = mock(DataJobLog.class);
        adapter = new RegulatoryCorpusDataSourceAdapter(regulatoryPort);
    }

    @Test
    void feedIdAndKind() {
        assertThat(adapter.feedId()).isEqualTo("regulatory");
        assertThat(adapter.kind()).isEqualTo(DataSourceKind.INTERNAL_CORPUS);
    }

    @Test
    void describeReturnsValidFeed() {
        DataFeed feed = adapter.describe();
        assertThat(feed.feedId()).isEqualTo("regulatory");
        assertThat(feed.label()).isEqualTo("Regulatory Events");
        assertThat(feed.active()).isTrue();
        assertThat(feed.parameters()).hasSize(2);
    }

    @Test
    void supportsMatchesFeedId() {
        DataRequest match = makeRequest("regulatory", Map.of(), null, 100);
        DataRequest noMatch = makeRequest("articles", Map.of(), null, 100);
        assertThat(adapter.supports(match)).isTrue();
        assertThat(adapter.supports(noMatch)).isFalse();
    }

    @Test
    void fetchRecentDefaultPath() {
        when(regulatoryPort.findRecent(100)).thenReturn(List.of(sampleEvent("e1"), sampleEvent("e2")));

        DataSet result = adapter.fetch(makeRequest("regulatory", Map.of(), null, 100), jobLog);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.columns()).hasSize(8);
        assertThat(result.rows().get(0).get(0)).isEqualTo("AI Diagnostic Tool");
        verify(regulatoryPort).findRecent(100);
    }

    @Test
    void fetchByBodyParam() {
        when(regulatoryPort.findByBody(RegulatoryBody.FDA, 100)).thenReturn(List.of(sampleEvent("e1")));

        DataSet result = adapter.fetch(makeRequest("regulatory", Map.of("body", "FDA"), null, 100), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(regulatoryPort).findByBody(RegulatoryBody.FDA, 100);
    }

    @Test
    void fetchByKeywordParam() {
        when(regulatoryPort.findByKeyword("ai diagnostics", 100)).thenReturn(List.of(sampleEvent("e1")));

        DataSet result = adapter.fetch(makeRequest("regulatory", Map.of("keyword", "ai diagnostics"), null, 100), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(regulatoryPort).findByKeyword("ai diagnostics", 100);
    }

    @Test
    void fetchByQueryPlanKeywords() {
        DataQueryPlan plan = new DataQueryPlan("regulatory", List.of("510k clearance"), null, null,
                List.of(), List.of(), null, 100);
        when(regulatoryPort.findByKeyword("510k clearance", 100)).thenReturn(List.of(sampleEvent("e1")));

        DataSet result = adapter.fetch(makeRequest("regulatory", Map.of(), plan, 100), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(regulatoryPort).findByKeyword("510k clearance", 100);
    }

    @Test
    void fetchByQueryPlanCategory() {
        DataQueryPlan plan = new DataQueryPlan("regulatory", List.of(), null, null,
                List.of(), List.of("CMS"), null, 100);
        when(regulatoryPort.findByBody(RegulatoryBody.CMS, 100)).thenReturn(List.of(sampleEvent("e1")));

        DataSet result = adapter.fetch(makeRequest("regulatory", Map.of(), plan, 100), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(regulatoryPort).findByBody(RegulatoryBody.CMS, 100);
    }

    @Test
    void rowLimitTruncatesAndSetsTruncatedCount() {
        List<RegulatoryEvent> events = List.of(sampleEvent("e1"), sampleEvent("e2"), sampleEvent("e3"));
        when(regulatoryPort.findRecent(2)).thenReturn(events);

        DataSet result = adapter.fetch(makeRequest("regulatory", Map.of(), null, 2), jobLog);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.truncatedAtRows()).isEqualTo(3);
    }

    @Test
    void emptyResultReturnsEmptyDataSet() {
        when(regulatoryPort.findRecent(100)).thenReturn(Collections.emptyList());

        DataSet result = adapter.fetch(makeRequest("regulatory", Map.of(), null, 100), jobLog);

        assertThat(result.rows()).isEmpty();
        assertThat(result.truncatedAtRows()).isZero();
    }

    @Test
    void rowWidthMatchesColumnCount() {
        when(regulatoryPort.findRecent(100)).thenReturn(List.of(sampleEvent("e1")));

        DataSet result = adapter.fetch(makeRequest("regulatory", Map.of(), null, 100), jobLog);

        assertThat(result.rows().get(0)).hasSize(result.columns().size());
    }

    private RegulatoryEvent sampleEvent(String id) {
        return new RegulatoryEvent(
                id, RegulatoryEventType.FDA_510K_CLEARANCE, RegulatoryBody.FDA,
                "AI Diagnostic Tool", "Test summary", "K241234",
                "Acme Corp", "AI Scanner", "https://fda.gov/test",
                null, Instant.now(), Instant.now(),
                List.of("ai", "diagnostics"),
                null, null, null, null
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
