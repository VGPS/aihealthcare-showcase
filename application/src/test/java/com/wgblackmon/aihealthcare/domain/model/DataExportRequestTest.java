package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataExportRequest} and {@link DataExportResult} records
 * and the {@link ExportFormat} enum.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
class DataExportRequestTest {

    @Test
    void validRequest_createsSuccessfully() {
        DataExportRequest req = new DataExportRequest("articles", ExportFormat.CSV, 50, "Acme Corp");
        assertThat(req.exportType()).isEqualTo("articles");
        assertThat(req.format()).isEqualTo(ExportFormat.CSV);
        assertThat(req.limit()).isEqualTo(50);
        assertThat(req.brandName()).isEqualTo("Acme Corp");
    }

    @Test
    void nullExportType_throws() {
        assertThatThrownBy(() -> new DataExportRequest(null, ExportFormat.CSV, 10, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankExportType_throws() {
        assertThatThrownBy(() -> new DataExportRequest("  ", ExportFormat.CSV, 10, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullFormat_throws() {
        assertThatThrownBy(() -> new DataExportRequest("articles", null, 10, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroLimit_throws() {
        assertThatThrownBy(() -> new DataExportRequest("articles", ExportFormat.CSV, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeLimit_throws() {
        assertThatThrownBy(() -> new DataExportRequest("articles", ExportFormat.JSON, -5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validResult_createsSuccessfully() {
        DataExportResult res = new DataExportResult("data", "file.csv", "text/csv", 10);
        assertThat(res.content()).isEqualTo("data");
        assertThat(res.filename()).isEqualTo("file.csv");
        assertThat(res.contentType()).isEqualTo("text/csv");
        assertThat(res.recordCount()).isEqualTo(10);
    }

    @Test
    void nullContent_throws() {
        assertThatThrownBy(() -> new DataExportResult(null, "file.csv", "text/csv", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankFilename_throws() {
        assertThatThrownBy(() -> new DataExportResult("data", "  ", "text/csv", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportFormat_hasThreeValues() {
        assertThat(ExportFormat.values()).hasSize(3);
        assertThat(ExportFormat.valueOf("CSV")).isEqualTo(ExportFormat.CSV);
        assertThat(ExportFormat.valueOf("JSON")).isEqualTo(ExportFormat.JSON);
        assertThat(ExportFormat.valueOf("PDF")).isEqualTo(ExportFormat.PDF);
    }

    @Test
    void nullBrandName_allowedInRequest() {
        DataExportRequest req = new DataExportRequest("deals", ExportFormat.JSON, 25, null);
        assertThat(req.brandName()).isNull();
    }
}
