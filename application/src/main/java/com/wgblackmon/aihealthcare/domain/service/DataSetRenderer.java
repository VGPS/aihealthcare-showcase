package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.DataColumn;
import com.wgblackmon.aihealthcare.domain.model.DataSet;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Renders a {@link DataSet} to a byte array in the requested {@link ExportFormat}.
 *
 * <p>Supports CSV and JSON formats for tabular data. LLM_SYNTHESIS feeds may
 * also carry a narrative; when present, it appears after data rows in CSV
 * (under {@code # Analysis}) or as a {@code "narrative"} field in JSON.
 *
 * <p>This class is a pure utility — no Spring annotations, no framework imports.
 * It owns the escape helpers previously duplicated in {@code DataExportService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-11
 */
public final class DataSetRenderer {

    private DataSetRenderer() {}

    /**
     * Renders the data set to a byte array.
     *
     * @param dataSet the data to render
     * @param format  CSV or JSON (PDF is not supported here)
     * @return UTF-8 encoded bytes
     * @throws IllegalArgumentException if format is PDF or null
     */
    public static byte[] render(DataSet dataSet, ExportFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("ExportFormat must not be null");
        }
        return switch (format) {
            case CSV -> renderCsv(dataSet);
            case JSON -> renderJson(dataSet);
            case PDF -> throw new IllegalArgumentException("PDF rendering is not supported by DataSetRenderer");
        };
    }

    private static byte[] renderCsv(DataSet dataSet) {
        StringBuilder csv = new StringBuilder();
        List<DataColumn> columns = dataSet.columns();

        for (int c = 0; c < columns.size(); c++) {
            if (c > 0) csv.append(",");
            csv.append(escapeCsv(columns.get(c).label()));
        }
        csv.append("\n");

        for (List<String> row : dataSet.rows()) {
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) csv.append(",");
                csv.append(escapeCsv(row.get(c)));
            }
            csv.append("\n");
        }

        if (!dataSet.citations().isEmpty()) {
            csv.append("\n# Sources\n");
            for (SourceCitation cit : dataSet.citations()) {
                csv.append("# [").append(cit.citationNumber()).append("] ")
                        .append(cit.title()).append(" — ").append(cit.url()).append("\n");
            }
        }

        if (dataSet.narrative() != null && !dataSet.narrative().isBlank()) {
            csv.append("\n# Analysis\n");
            for (String line : dataSet.narrative().split("\n")) {
                csv.append("# ").append(line).append("\n");
            }
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] renderJson(DataSet dataSet) {
        StringBuilder json = new StringBuilder("{");

        if (dataSet.narrative() != null && !dataSet.narrative().isBlank()) {
            json.append("\"narrative\":\"").append(escapeJson(dataSet.narrative())).append("\",");
        }

        json.append("\"columns\":[");
        List<DataColumn> columns = dataSet.columns();
        for (int c = 0; c < columns.size(); c++) {
            if (c > 0) json.append(",");
            DataColumn col = columns.get(c);
            json.append("{\"name\":\"").append(escapeJson(col.name()))
                    .append("\",\"label\":\"").append(escapeJson(col.label()))
                    .append("\",\"type\":\"").append(escapeJson(col.type()))
                    .append("\"}");
        }
        json.append("],\"rows\":[");

        List<List<String>> rows = dataSet.rows();
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) json.append(",");
            json.append("[");
            List<String> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) json.append(",");
                json.append("\"").append(escapeJson(row.get(c))).append("\"");
            }
            json.append("]");
        }
        json.append("]");

        if (!dataSet.citations().isEmpty()) {
            json.append(",\"citations\":[");
            List<SourceCitation> cits = dataSet.citations();
            for (int i = 0; i < cits.size(); i++) {
                if (i > 0) json.append(",");
                SourceCitation cit = cits.get(i);
                json.append("{\"number\":").append(cit.citationNumber())
                        .append(",\"title\":\"").append(escapeJson(cit.title()))
                        .append("\",\"url\":\"").append(escapeJson(cit.url()))
                        .append("\"}");
            }
            json.append("]");
        }

        json.append("}");
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Escapes a value for safe inclusion in a CSV cell.
     * Wraps in double quotes if the value contains comma, quote, or newline.
     */
    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Escapes a value for safe inclusion inside a JSON string literal.
     */
    public static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Escapes a value for safe inclusion in HTML content.
     */
    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
