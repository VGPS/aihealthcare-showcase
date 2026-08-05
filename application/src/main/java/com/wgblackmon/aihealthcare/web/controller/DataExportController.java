package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DataExportRequest;
import com.wgblackmon.aihealthcare.domain.model.DataExportResult;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.port.inbound.ExportDataUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for data export endpoints. Supports CSV, JSON, and
 * PDF (HTML) exports with optional white-label branding.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/export")
public class DataExportController {

    private final ExportDataUseCase exportDataUseCase;

    public DataExportController(ExportDataUseCase exportDataUseCase) {
        log.debug("DataExportController() | exportDataUseCase={}", exportDataUseCase.getClass().getSimpleName());
        this.exportDataUseCase = exportDataUseCase;
    }

    @GetMapping
    public ResponseEntity<byte[]> exportData(
            @RequestParam String type,
            @RequestParam(defaultValue = "CSV") String format,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String brandName) {
        log.debug("exportData() | type={}, format={}, limit={}, brandName={}", type, format, limit, brandName);

        ExportFormat exportFormat;
        try {
            exportFormat = ExportFormat.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.debug("exportData() | return=400 invalid format");
            return ResponseEntity.badRequest().build();
        }

        DataExportRequest request = new DataExportRequest(type, exportFormat, limit, brandName);
        DataExportResult result = exportDataUseCase.export(request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.contentType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"");

        log.debug("exportData() | return={} records, filename={}", result.recordCount(), result.filename());
        return ResponseEntity.ok()
                .headers(headers)
                .body(result.content().getBytes());
    }
}
