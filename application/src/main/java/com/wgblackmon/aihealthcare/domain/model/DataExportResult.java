package com.wgblackmon.aihealthcare.domain.model;

/**
 * The result of a data export operation.
 *
 * @param content      the exported data (CSV string, JSON string, or base64-encoded PDF)
 * @param filename     suggested filename for download
 * @param contentType  MIME type (text/csv, application/json, application/pdf)
 * @param recordCount  number of records exported
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public record DataExportResult(
        String content,
        String filename,
        String contentType,
        int recordCount
) {

    public DataExportResult {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
    }
}
