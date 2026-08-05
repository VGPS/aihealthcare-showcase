package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.DataExportRequest;
import com.wgblackmon.aihealthcare.domain.model.DataExportResult;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.ExportDataUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyRelationshipPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealSignalPort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Domain service that exports data in CSV, JSON, or PDF-ready format
 * with optional white-label branding for enterprise customers.
 *
 * <p>Supports exporting articles, deal signals, and company relationships.
 * PDF exports produce HTML content suitable for client-side rendering.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public class DataExportService implements ExportDataUseCase {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ArticleIngestionPort articleIngestionPort;
    private final DealSignalPort dealSignalPort;
    private final CompanyRelationshipPort companyRelationshipPort;

    public DataExportService(ArticleIngestionPort articleIngestionPort,
                              DealSignalPort dealSignalPort,
                              CompanyRelationshipPort companyRelationshipPort) {
        this.articleIngestionPort = articleIngestionPort;
        this.dealSignalPort = dealSignalPort;
        this.companyRelationshipPort = companyRelationshipPort;
    }

    @Override
    public DataExportResult export(DataExportRequest request) {
        switch (request.exportType()) {
            case "articles":
                return exportArticles(request);
            case "deals":
                return exportDealSignals(request);
            case "relationships":
                return exportRelationships(request);
            default:
                throw new IllegalArgumentException("Unknown export type: " + request.exportType());
        }
    }

    private DataExportResult exportArticles(DataExportRequest request) {
        List<NewsArticle> articles = articleIngestionPort.fetchRecentArticles(30);
        int count = Math.min(articles.size(), request.limit());
        String dateStr = LocalDate.now(ZoneOffset.UTC).toString();

        if (request.format() == ExportFormat.CSV) {
            StringBuilder csv = new StringBuilder();
            csv.append("articleId,title,url,topic,sourceName,publishedAt\n");
            for (int i = 0; i < count; i++) {
                NewsArticle a = articles.get(i);
                csv.append(escapeCsv(a.articleId())).append(",");
                csv.append(escapeCsv(a.title())).append(",");
                csv.append(escapeCsv(a.url().toString())).append(",");
                csv.append(escapeCsv(a.topic())).append(",");
                csv.append(escapeCsv(a.sourceName() != null ? a.sourceName() : "")).append(",");
                csv.append(a.publishedAt() != null ? DATE_FMT.format(a.publishedAt()) : "").append("\n");
            }
            return new DataExportResult(csv.toString(),
                    "articles-" + dateStr + ".csv", "text/csv", count);
        }

        if (request.format() == ExportFormat.JSON) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < count; i++) {
                if (i > 0) json.append(",");
                NewsArticle a = articles.get(i);
                json.append("{");
                json.append("\"articleId\":\"").append(escapeJson(a.articleId())).append("\",");
                json.append("\"title\":\"").append(escapeJson(a.title())).append("\",");
                json.append("\"url\":\"").append(escapeJson(a.url().toString())).append("\",");
                json.append("\"topic\":\"").append(escapeJson(a.topic())).append("\"");
                json.append("}");
            }
            json.append("]");
            return new DataExportResult(json.toString(),
                    "articles-" + dateStr + ".json", "application/json", count);
        }

        return buildPdfHtml("Articles Report", request.brandName(), count + " articles exported", count);
    }

    private DataExportResult exportDealSignals(DataExportRequest request) {
        List<DealSignal> signals = dealSignalPort.findRecent(request.limit());
        int count = signals.size();
        String dateStr = LocalDate.now(ZoneOffset.UTC).toString();

        if (request.format() == ExportFormat.CSV) {
            StringBuilder csv = new StringBuilder();
            csv.append("signalId,title,signalType,companyName,confidence,detectedAt\n");
            for (DealSignal s : signals) {
                csv.append(escapeCsv(s.signalId())).append(",");
                csv.append(escapeCsv(s.title())).append(",");
                csv.append(s.signalType().name()).append(",");
                csv.append(escapeCsv(s.companyName() != null ? s.companyName() : "")).append(",");
                csv.append(String.format("%.2f", s.confidence())).append(",");
                csv.append(DATE_FMT.format(s.detectedAt())).append("\n");
            }
            return new DataExportResult(csv.toString(),
                    "deal-signals-" + dateStr + ".csv", "text/csv", count);
        }

        if (request.format() == ExportFormat.JSON) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < count; i++) {
                if (i > 0) json.append(",");
                DealSignal s = signals.get(i);
                json.append("{");
                json.append("\"signalId\":\"").append(escapeJson(s.signalId())).append("\",");
                json.append("\"title\":\"").append(escapeJson(s.title())).append("\",");
                json.append("\"signalType\":\"").append(s.signalType().name()).append("\",");
                json.append("\"confidence\":").append(s.confidence());
                json.append("}");
            }
            json.append("]");
            return new DataExportResult(json.toString(),
                    "deal-signals-" + dateStr + ".json", "application/json", count);
        }

        return buildPdfHtml("Deal Signals Report", request.brandName(), count + " signals exported", count);
    }

    private DataExportResult exportRelationships(DataExportRequest request) {
        List<CompanyRelationship> rels = companyRelationshipPort.findAll();
        int count = Math.min(rels.size(), request.limit());
        String dateStr = LocalDate.now(ZoneOffset.UTC).toString();

        if (request.format() == ExportFormat.CSV) {
            StringBuilder csv = new StringBuilder();
            csv.append("sourceCompany,targetCompany,relationshipType,confidence,detectedAt\n");
            for (int i = 0; i < count; i++) {
                CompanyRelationship r = rels.get(i);
                csv.append(escapeCsv(r.sourceCompany())).append(",");
                csv.append(escapeCsv(r.targetCompany())).append(",");
                csv.append(r.relationshipType().name()).append(",");
                csv.append(String.format("%.2f", r.confidence())).append(",");
                csv.append(DATE_FMT.format(r.detectedAt())).append("\n");
            }
            return new DataExportResult(csv.toString(),
                    "relationships-" + dateStr + ".csv", "text/csv", count);
        }

        if (request.format() == ExportFormat.JSON) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < count; i++) {
                if (i > 0) json.append(",");
                CompanyRelationship r = rels.get(i);
                json.append("{");
                json.append("\"sourceCompany\":\"").append(escapeJson(r.sourceCompany())).append("\",");
                json.append("\"targetCompany\":\"").append(escapeJson(r.targetCompany())).append("\",");
                json.append("\"relationshipType\":\"").append(r.relationshipType().name()).append("\",");
                json.append("\"confidence\":").append(r.confidence());
                json.append("}");
            }
            json.append("]");
            return new DataExportResult(json.toString(),
                    "relationships-" + dateStr + ".json", "application/json", count);
        }

        return buildPdfHtml("Company Relationships Report", request.brandName(),
                count + " relationships exported", count);
    }

    private DataExportResult buildPdfHtml(String title, String brandName, String summary, int count) {
        String brand = (brandName != null && !brandName.isBlank()) ? brandName : "AI Healthcare Intelligence";
        String dateStr = LocalDate.now(ZoneOffset.UTC).toString();
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>body{font-family:Arial,sans-serif;margin:40px;}");
        html.append("h1{color:#1a365d;}h2{color:#2d3748;}.brand{font-size:24px;font-weight:bold;color:#2b6cb0;}");
        html.append(".footer{margin-top:40px;color:#718096;font-size:12px;}</style></head><body>");
        html.append("<div class='brand'>").append(escapeHtml(brand)).append("</div>");
        html.append("<h1>").append(escapeHtml(title)).append("</h1>");
        html.append("<p>Generated: ").append(dateStr).append("</p>");
        html.append("<p>").append(escapeHtml(summary)).append("</p>");
        html.append("<div class='footer'>Confidential — ").append(escapeHtml(brand)).append("</div>");
        html.append("</body></html>");
        return new DataExportResult(html.toString(),
                title.toLowerCase().replace(' ', '-') + "-" + dateStr + ".html",
                "text/html", count);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
