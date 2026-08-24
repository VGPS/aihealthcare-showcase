package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.CompanyRelationshipType;
import com.wgblackmon.aihealthcare.domain.model.DataExportRequest;
import com.wgblackmon.aihealthcare.domain.model.DataExportResult;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyRelationshipPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealSignalPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataExportService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
class DataExportServiceTest {

    private ArticleIngestionPort articleIngestionPort;
    private DealSignalPort dealSignalPort;
    private CompanyRelationshipPort companyRelationshipPort;
    private DataExportService service;

    @BeforeEach
    void setUp() {
        articleIngestionPort = Mockito.mock(ArticleIngestionPort.class);
        dealSignalPort = Mockito.mock(DealSignalPort.class);
        companyRelationshipPort = Mockito.mock(CompanyRelationshipPort.class);
        service = new DataExportService(articleIngestionPort, dealSignalPort, companyRelationshipPort);
    }

    @Test
    void exportArticles_csv_returnsValidCsv() {
        NewsArticle article = new NewsArticle(
                "a1", "Test Article", URI.create("https://example.com/a1"),
                "body", "AI Healthcare", null, null, "PubMed", "ACADEMIC", 0.9,
                Instant.parse("2026-01-15T00:00:00Z"));
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(article));

        DataExportRequest request = new DataExportRequest("articles", ExportFormat.CSV, 100, null);
        DataExportResult result = service.export(request);

        assertThat(result.contentType()).isEqualTo("text/csv");
        assertThat(result.content()).contains("articleId,title,url");
        assertThat(result.content()).contains("a1");
        assertThat(result.content()).contains("Test Article");
        assertThat(result.recordCount()).isEqualTo(1);
        assertThat(result.filename()).startsWith("articles-").endsWith(".csv");
    }

    @Test
    void exportArticles_json_returnsValidJson() {
        NewsArticle article = new NewsArticle(
                "a2", "JSON Article", URI.create("https://example.com/a2"),
                "body", "AI Healthcare", null, null, "PubMed", "ACADEMIC", 0.9, null);
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(article));

        DataExportRequest request = new DataExportRequest("articles", ExportFormat.JSON, 100, null);
        DataExportResult result = service.export(request);

        assertThat(result.contentType()).isEqualTo("application/json");
        assertThat(result.content()).startsWith("[");
        assertThat(result.content()).contains("\"articleId\":\"a2\"");
        assertThat(result.filename()).endsWith(".json");
    }

    @Test
    void exportArticles_pdf_returnsHtml() {
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of());

        DataExportRequest request = new DataExportRequest("articles", ExportFormat.PDF, 100, "Custom Brand");
        DataExportResult result = service.export(request);

        assertThat(result.contentType()).isEqualTo("text/html");
        assertThat(result.content()).contains("Custom Brand");
        assertThat(result.content()).contains("Articles Report");
    }

    @Test
    void exportDeals_csv_returnsValidCsv() {
        DealSignal signal = new DealSignal(
                "ds1", "art1", "Funding Round", DealSignalType.FUNDING,
                "Acme Health", "Series B", 0.85, Instant.now(),
                null, null, null, null);
        when(dealSignalPort.findRecent(50, 0)).thenReturn(List.of(signal));

        DataExportRequest request = new DataExportRequest("deals", ExportFormat.CSV, 50, null);
        DataExportResult result = service.export(request);

        assertThat(result.contentType()).isEqualTo("text/csv");
        assertThat(result.content()).contains("signalId,title");
        assertThat(result.content()).contains("ds1");
        assertThat(result.content()).contains("FUNDING");
        assertThat(result.recordCount()).isEqualTo(1);
    }

    @Test
    void exportDeals_json_returnsValidJson() {
        DealSignal signal = new DealSignal(
                "ds2", "art2", "Acquisition", DealSignalType.ACQUISITION,
                "BigCo", "Acquired startup", 0.9, Instant.now(),
                null, null, null, null);
        when(dealSignalPort.findRecent(50, 0)).thenReturn(List.of(signal));

        DataExportRequest request = new DataExportRequest("deals", ExportFormat.JSON, 50, null);
        DataExportResult result = service.export(request);

        assertThat(result.contentType()).isEqualTo("application/json");
        assertThat(result.content()).contains("\"signalType\":\"ACQUISITION\"");
    }

    @Test
    void exportRelationships_csv_returnsValidCsv() {
        CompanyRelationship rel = new CompanyRelationship(
                "r1", "Acme", "BetaCorp", CompanyRelationshipType.PARTNERSHIP,
                "art1", "Strategic partnership", 0.7, Instant.now());
        when(companyRelationshipPort.findAll()).thenReturn(List.of(rel));

        DataExportRequest request = new DataExportRequest("relationships", ExportFormat.CSV, 100, null);
        DataExportResult result = service.export(request);

        assertThat(result.contentType()).isEqualTo("text/csv");
        assertThat(result.content()).contains("sourceCompany,targetCompany");
        assertThat(result.content()).contains("Acme");
        assertThat(result.content()).contains("BetaCorp");
    }

    @Test
    void exportRelationships_json_returnsValidJson() {
        CompanyRelationship rel = new CompanyRelationship(
                "r2", "Alpha", "Gamma", CompanyRelationshipType.ACQUISITION,
                "art2", "Alpha acquired Gamma", 0.95, Instant.now());
        when(companyRelationshipPort.findAll()).thenReturn(List.of(rel));

        DataExportRequest request = new DataExportRequest("relationships", ExportFormat.JSON, 100, null);
        DataExportResult result = service.export(request);

        assertThat(result.contentType()).isEqualTo("application/json");
        assertThat(result.content()).contains("\"sourceCompany\":\"Alpha\"");
        assertThat(result.content()).contains("\"relationshipType\":\"ACQUISITION\"");
    }

    @Test
    void unknownExportType_throwsIllegalArgument() {
        DataExportRequest request = new DataExportRequest("unknown", ExportFormat.CSV, 10, null);
        assertThatThrownBy(() -> service.export(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown export type");
    }

    @Test
    void pdfExport_defaultBrand_usesAiHealthcareIntelligence() {
        when(dealSignalPort.findRecent(10, 0)).thenReturn(List.of());

        DataExportRequest request = new DataExportRequest("deals", ExportFormat.PDF, 10, null);
        DataExportResult result = service.export(request);

        assertThat(result.content()).contains("AI Healthcare Intelligence");
    }

    @Test
    void csvEscaping_handlesCommasAndQuotes() {
        NewsArticle article = new NewsArticle(
                "a3", "Title, with \"quotes\"", URI.create("https://example.com/a3"),
                "body", "AI Healthcare", null, null, null, null, 0.5, null);
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(article));

        DataExportRequest request = new DataExportRequest("articles", ExportFormat.CSV, 100, null);
        DataExportResult result = service.export(request);

        assertThat(result.content()).contains("\"Title, with \"\"quotes\"\"\"");
    }
}
