package com.wgblackmon.aihealthcare.domain.model;

/**
 * A request to export data in a specified format and scope.
 *
 * @param exportType  what data to export (articles, signals, relationships, etc.)
 * @param format      output format (CSV, JSON, PDF)
 * @param limit       maximum number of records to include
 * @param brandName   optional white-label brand name for PDF reports
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public record DataExportRequest(
        String exportType,
        ExportFormat format,
        int limit,
        String brandName
) {

    public DataExportRequest {
        if (exportType == null || exportType.isBlank()) {
            throw new IllegalArgumentException("exportType must not be blank");
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
    }
}
