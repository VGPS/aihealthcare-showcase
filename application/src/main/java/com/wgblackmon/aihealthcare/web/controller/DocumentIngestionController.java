package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DocumentIngestionResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestDocumentsUseCase;
import com.wgblackmon.aihealthcare.web.dto.DocumentIngestRequest;
import com.wgblackmon.aihealthcare.web.dto.DocumentIngestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 * @updated 2026-04-27
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentIngestionController {

    private final IngestDocumentsUseCase ingestUseCase;

    public DocumentIngestionController(IngestDocumentsUseCase ingestUseCase) {
        log.debug("DocumentIngestionController() | ingestUseCase={}",
                  ingestUseCase.getClass().getSimpleName());
        this.ingestUseCase = ingestUseCase;
    }

    /**
     * Ingest all supported documents found in the specified directory.
     *
     * @param request Ingestion parameters (directory path, source label, optional chunk size).
     * @return 200 OK with a {@link DocumentIngestResponse} summarising the run.
     */
    @PostMapping("/ingest")
    public ResponseEntity<DocumentIngestResponse> ingest(
            @RequestBody DocumentIngestRequest request) {
        log.debug("ingest() | request={}", request);

        int chunkSize = request.chunkSize() != null ? request.chunkSize() : 1000;

        DocumentIngestionResult result = ingestUseCase.ingest(
                request.directory(),
                request.sourceLabel(),
                chunkSize);

        DocumentIngestResponse response = new DocumentIngestResponse(
                result.filesProcessed(),
                result.chunksEmbedded(),
                result.failures());

        log.info("ingest() | directory={}, filesProcessed={}, chunksEmbedded={}",
                 request.directory(), result.filesProcessed(), result.chunksEmbedded());
        log.debug("ingest() | return={}", response);
        return ResponseEntity.ok(response);
    }
}
