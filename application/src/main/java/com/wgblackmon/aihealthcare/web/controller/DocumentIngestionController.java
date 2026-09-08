package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.inbound.IngestDocumentsUseCase;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineAsyncRunner;
import com.wgblackmon.aihealthcare.web.dto.DocumentIngestRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller exposing the document-ingestion endpoint.
 *
 * <p>Accepts a directory path and source label, delegates to
 * {@link IngestDocumentsUseCase}, and returns a summary of files processed,
 * chunks embedded, and any per-file failures.
 *
 * <p>An {@link IllegalArgumentException} thrown by the use case (invalid directory)
 * is mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-09-08
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentIngestionController {

    private final IngestDocumentsUseCase ingestUseCase;
    private final PipelineAsyncRunner asyncRunner;

    public DocumentIngestionController(IngestDocumentsUseCase ingestUseCase,
                                        PipelineAsyncRunner asyncRunner) {
        log.debug("DocumentIngestionController() | ingestUseCase={}, asyncRunner={}",
                  ingestUseCase.getClass().getSimpleName(),
                  asyncRunner.getClass().getSimpleName());
        this.ingestUseCase = ingestUseCase;
        this.asyncRunner = asyncRunner;
    }

    /**
     * Ingest all supported documents found in the specified directory.
     *
     * @param request Ingestion parameters (directory path, source label, optional chunk size).
     * @return 202 Accepted with pipeline started metadata.
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestBody DocumentIngestRequest request) {
        log.debug("ingest() | request={}", request);

        int chunkSize = request.chunkSize() != null ? request.chunkSize() : 1000;
        String directory = request.directory();
        String sourceLabel = request.sourceLabel();

        return asyncRunner.runAsync("document-ingest", () ->
                ingestUseCase.ingest(directory, sourceLabel, chunkSize));
    }
}
