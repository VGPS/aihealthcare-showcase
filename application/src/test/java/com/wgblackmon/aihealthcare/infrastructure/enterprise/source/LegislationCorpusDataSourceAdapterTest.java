package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import com.wgblackmon.aihealthcare.domain.port.outbound.StateLawPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LegislationCorpusDataSourceAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-11
 */
class LegislationCorpusDataSourceAdapterTest {

    private StateLawPort stateLawPort;
    private DataJobLog jobLog;
    private LegislationCorpusDataSourceAdapter adapter;

    @BeforeEach
    void setUp() {
        stateLawPort = mock(StateLawPort.class);
        jobLog = mock(DataJobLog.class);
        adapter = new LegislationCorpusDataSourceAdapter(stateLawPort);
    }

    @Test
    void feedIdAndKind() {
        assertThat(adapter.feedId()).isEqualTo("legislation");
        assertThat(adapter.kind()).isEqualTo(DataSourceKind.INTERNAL_CORPUS);
    }

    @Test
    void describeReturnsValidFeed() {
        DataFeed feed = adapter.describe();
        assertThat(feed.feedId()).isEqualTo("legislation");
        assertThat(feed.label()).isEqualTo("State Health-AI Legislation");
        assertThat(feed.active()).isTrue();
        assertThat(feed.parameters()).hasSize(2);
    }

    @Test
    void supportsMatchesFeedId() {
        DataRequest match = makeRequest("legislation", Map.of(), null, 100);
        DataRequest noMatch = makeRequest("articles", Map.of(), null, 100);
        assertThat(adapter.supports(match)).isTrue();
        assertThat(adapter.supports(noMatch)).isFalse();
    }

    @Test
    void fetchAllDefaultPath() {
        when(stateLawPort.findAll()).thenReturn(List.of(sampleLaw("ca-ab-3030"), sampleLaw("co-sb-205")));

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of(), null, 100), jobLog);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.columns()).hasSize(12);
        assertThat(result.rows().get(0).get(0)).isEqualTo("ca-ab-3030");
        verify(stateLawPort).findAll();
    }

    @Test
    void fetchByStateParam() {
        when(stateLawPort.findByState(StateCode.CA)).thenReturn(List.of(sampleLaw("ca-ab-3030")));

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of("state", "CA"), null, 100), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(stateLawPort).findByState(StateCode.CA);
    }

    @Test
    void fetchByCategoryParam() {
        when(stateLawPort.findByCategory(LawCategory.CLAIMS_DOWNCODING)).thenReturn(List.of(sampleLaw("ca-ab-3030")));

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of("category", "CLAIMS_DOWNCODING"), null, 100), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(stateLawPort).findByCategory(LawCategory.CLAIMS_DOWNCODING);
    }

    @Test
    void fetchByQueryPlanState() {
        DataQueryPlan plan = new DataQueryPlan("legislation", List.of(), null, null,
                List.of("CO"), List.of(), null, 100);
        when(stateLawPort.findByState(StateCode.CO)).thenReturn(List.of(sampleLaw("co-sb-205")));

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of(), plan, 100), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(stateLawPort).findByState(StateCode.CO);
    }

    @Test
    void fetchByQueryPlanCategory() {
        DataQueryPlan plan = new DataQueryPlan("legislation", List.of(), null, null,
                List.of(), List.of("PROVIDER_CLINICAL_USE"), null, 100);
        when(stateLawPort.findByCategory(LawCategory.PROVIDER_CLINICAL_USE)).thenReturn(List.of(sampleLaw("ca-ab-3030")));

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of(), plan, 100), jobLog);

        assertThat(result.rows()).hasSize(1);
        verify(stateLawPort).findByCategory(LawCategory.PROVIDER_CLINICAL_USE);
    }

    @Test
    void rowLimitTruncatesAndSetsTruncatedCount() {
        List<StateLaw> laws = List.of(sampleLaw("a"), sampleLaw("b"), sampleLaw("c"));
        when(stateLawPort.findAll()).thenReturn(laws);

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of(), null, 2), jobLog);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.truncatedAtRows()).isEqualTo(3);
    }

    @Test
    void emptyResultReturnsEmptyDataSet() {
        when(stateLawPort.findAll()).thenReturn(Collections.emptyList());

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of(), null, 100), jobLog);

        assertThat(result.rows()).isEmpty();
        assertThat(result.truncatedAtRows()).isZero();
    }

    @Test
    void rowWidthMatchesColumnCount() {
        when(stateLawPort.findAll()).thenReturn(List.of(sampleLaw("ca-ab-3030")));

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of(), null, 100), jobLog);

        assertThat(result.rows().get(0)).hasSize(result.columns().size());
    }

    @Test
    void categoriesArePipeDelimited() {
        StateLaw law = new StateLaw(
                "ca-ab-3030", StateCode.CA, "California", "AB-3030",
                "Health AI Transparency", 2024, "2024-09-28", null,
                "2025-01-01", null, LawStatus.ENACTED, null,
                List.of(LawCategory.PAYER_UTILIZATION_REVIEW, LawCategory.CLAIMS_DOWNCODING),
                "Insurers", "Disclose AI usage", "Fines",
                List.of(), null, "1.0", Instant.now(), Instant.now()
        );
        when(stateLawPort.findAll()).thenReturn(List.of(law));

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of(), null, 100), jobLog);

        assertThat(result.rows().get(0).get(9)).isEqualTo("PAYER_UTILIZATION_REVIEW|CLAIMS_DOWNCODING");
    }

    @Test
    void sourceUrlExtractsOfficialUrl() {
        LawSource official = new LawSource(SourceType.OFFICIAL,
                "https://leginfo.ca.gov/ab-3030", null, null, null, false);
        LawSource secondary = new LawSource(SourceType.SECONDARY,
                "https://news.example.com/ab-3030", null, null, null, false);
        StateLaw law = new StateLaw(
                "ca-ab-3030", StateCode.CA, "California", "AB-3030",
                "Health AI Transparency", 2024, "2024-09-28", null,
                "2025-01-01", null, LawStatus.ENACTED, null,
                List.of(LawCategory.PAYER_UTILIZATION_REVIEW),
                "Insurers", "Disclose AI usage", "Fines",
                List.of(secondary, official), null, "1.0", Instant.now(), Instant.now()
        );
        when(stateLawPort.findAll()).thenReturn(List.of(law));

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of(), null, 100), jobLog);

        assertThat(result.rows().get(0).get(5)).isEqualTo("https://leginfo.ca.gov/ab-3030");
    }

    @Test
    void sourceUrlFallsBackToFirstWhenNoOfficial() {
        LawSource secondary = new LawSource(SourceType.SECONDARY,
                "https://news.example.com/analysis", null, null, null, false);
        StateLaw law = new StateLaw(
                "ca-ab-3030", StateCode.CA, "California", "AB-3030",
                "Health AI Transparency", 2024, "2024-09-28", null,
                "2025-01-01", null, LawStatus.ENACTED, null,
                List.of(LawCategory.PAYER_UTILIZATION_REVIEW),
                "Insurers", "Disclose AI usage", "Fines",
                List.of(secondary), null, "1.0", Instant.now(), Instant.now()
        );
        when(stateLawPort.findAll()).thenReturn(List.of(law));

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of(), null, 100), jobLog);

        assertThat(result.rows().get(0).get(5)).isEqualTo("https://news.example.com/analysis");
    }

    @Test
    void sourceUrlEmptyWhenNoSources() {
        StateLaw law = new StateLaw(
                "ca-ab-3030", StateCode.CA, "California", "AB-3030",
                "Health AI Transparency", 2024, "2024-09-28", null,
                "2025-01-01", null, LawStatus.ENACTED, null,
                List.of(LawCategory.PAYER_UTILIZATION_REVIEW),
                "Insurers", "Disclose AI usage", "Fines",
                emptyList(), null, "1.0", Instant.now(), Instant.now()
        );
        when(stateLawPort.findAll()).thenReturn(List.of(law));

        DataSet result = adapter.fetch(makeRequest("legislation", Map.of(), null, 100), jobLog);

        assertThat(result.rows().get(0).get(5)).isEmpty();
    }

    private StateLaw sampleLaw(String id) {
        LawSource source = new LawSource(SourceType.OFFICIAL,
                "https://leginfo.ca.gov/" + id, null, null, null, false);
        return new StateLaw(
                id, StateCode.CA, "California", "AB-3030",
                "Health AI Transparency", 2024, "2024-09-28", null,
                "2025-01-01", null, LawStatus.ENACTED, null,
                List.of(LawCategory.PAYER_UTILIZATION_REVIEW),
                "Insurers", "Disclose AI usage", "Fines",
                List.of(source), null, "1.0", Instant.now(), Instant.now()
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
