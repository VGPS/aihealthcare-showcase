package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;
import com.wgblackmon.aihealthcare.domain.model.DocumentRecord;
import com.wgblackmon.aihealthcare.domain.model.DocumentIngestionResult;
import com.wgblackmon.aihealthcare.domain.model.DocumentStatus;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestDocumentsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentLibraryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FileParserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.KnowledgeCompilationPort;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application service that orchestrates the full document upload pipeline:
 * save record → ingest to vector store → compile into wiki → update status.
 *
 * <p>Called by {@link com.wgblackmon.aihealthcare.web.controller.DocumentLibraryController}
 * for browser-uploaded files.  Uses paragraph-aware chunking via
 * {@link IngestDocumentsUseCase#ingestFile(Path, String)}.
 *
 * <p>The {@code knowledgeCompilationPort} may be null (e.g., when the wiki compilation
 * adapter is unavailable). In that case, the document is left in INDEXED status.
 *
 * <p>This class carries no Spring annotations; {@code AppConfig} wires it as a
 * {@code @Bean} so the application layer remains framework-free.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-25
 * @updated 2026-08-25
 */
@Slf4j
public class DocumentUploadService {

    private static final String DOCS_BACK_URL = "https://app.bigskylabs.ai/admin/documents";

    private final IngestDocumentsUseCase ingestUseCase;
    private final DocumentLibraryPort    documentLibraryPort;
    private final KnowledgeCompilationPort knowledgeCompilationPort;
    private final List<FileParserPort>   parsers;

    public DocumentUploadService(IngestDocumentsUseCase ingestUseCase,
                                  DocumentLibraryPort documentLibraryPort,
                                  KnowledgeCompilationPort knowledgeCompilationPort,
                                  List<FileParserPort> parsers) {
        log.debug("DocumentUploadService() | ingestUseCase={}, documentLibraryPort={}, knowledgeCompilationPort={}, parsers={}",
                ingestUseCase.getClass().getSimpleName(),
                documentLibraryPort.getClass().getSimpleName(),
                knowledgeCompilationPort != null ? knowledgeCompilationPort.getClass().getSimpleName() : "null",
                parsers.size());
        this.ingestUseCase            = ingestUseCase;
        this.documentLibraryPort      = documentLibraryPort;
        this.knowledgeCompilationPort = knowledgeCompilationPort;
        this.parsers                  = parsers;
    }

    /**
     * Orchestrates the full upload pipeline for a single file.
     *
     * <p>Saves an UPLOADED record first so the library page shows progress even
     * if a later step fails. Status advances to INDEXED after vector store and
     * WIKI_COMPILED after successful wiki compilation.
     *
     * @param file        Path to the saved file on disk.
     * @param filename    Original filename as uploaded.
     * @param sourceLabel Human-readable attribution label from the upload form.
     * @param topic       Topic to group this document under (drives wiki/news grouping).
     * @return The final {@link DocumentRecord} after all pipeline steps complete.
     */
    public DocumentRecord uploadAndIngest(Path file, String filename, String sourceLabel, String topic) {
        log.debug("uploadAndIngest() | file={}, filename={}, sourceLabel={}, topic={}",
                file, filename, sourceLabel, topic);

        String docId = UUID.randomUUID().toString();
        DocumentRecord initial = new DocumentRecord(
                docId, filename, sourceLabel, topic, null, null, Instant.now(), 0, null,
                DocumentStatus.UPLOADED, null);
        documentLibraryPort.save(initial);

        DocumentIngestionResult ingestionResult;
        try {
            ingestionResult = ingestUseCase.ingestFile(file, sourceLabel);
        } catch (Exception e) {
            log.error("uploadAndIngest() | vector store ingestion failed for docId={}: {}", docId, e.getMessage());
            documentLibraryPort.updateStatus(docId, DocumentStatus.FAILED, 0, null, e.getMessage());
            return documentLibraryPort.findById(docId).orElse(initial);
        }

        if (!ingestionResult.failures().isEmpty()) {
            String err = ingestionResult.failures().get(0);
            documentLibraryPort.updateStatus(docId, DocumentStatus.FAILED, 0, null, err);
            log.warn("uploadAndIngest() | ingestion reported failure for docId={}: {}", docId, err);
            DocumentRecord result = documentLibraryPort.findById(docId).orElse(initial);
            log.debug("uploadAndIngest() | return={}", result.status());
            return result;
        }

        int chunkCount = ingestionResult.chunksEmbedded();
        if (chunkCount == 0) {
            String err = "No extractable text found in '" + filename
                    + "' — it may be a scanned/image-only PDF, empty, or corrupted.";
            documentLibraryPort.updateStatus(docId, DocumentStatus.FAILED, 0, null, err);
            log.warn("uploadAndIngest() | zero chunks extracted for docId={}, filename={}", docId, filename);
            DocumentRecord result = documentLibraryPort.findById(docId).orElse(initial);
            log.debug("uploadAndIngest() | return={}", result.status());
            return result;
        }

        documentLibraryPort.updateStatus(docId, DocumentStatus.INDEXED, chunkCount, null, null);

        if (knowledgeCompilationPort != null) {
            try {
                String fullText = extractFullText(file);
                if (!fullText.isBlank()) {
                    List<NewsArticle> pseudoArticles = new ArrayList<>();
                    pseudoArticles.add(new NewsArticle(
                            docId,
                            sourceLabel + " — " + filename,
                            URI.create(DOCS_BACK_URL),
                            fullText,
                            topic,
                            null, null,
                            sourceLabel,
                            "ACADEMIC",
                            0.85,
                            Instant.now()
                    ));
                    CompilationReport report = knowledgeCompilationPort.compileNewSources(pseudoArticles);
                    String slug = pickSlug(report);
                    documentLibraryPort.updateStatus(docId, DocumentStatus.WIKI_COMPILED, chunkCount, slug, null);
                    log.info("uploadAndIngest() | wiki compiled for docId={}, slug={}", docId, slug);
                }
            } catch (Exception e) {
                String err = "Wiki compilation failed: " + e.getMessage();
                documentLibraryPort.updateStatus(docId, DocumentStatus.INDEXED, chunkCount, null, err);
                log.warn("uploadAndIngest() | wiki compilation failed for docId={}: {}", docId, e.getMessage());
            }
        }

        DocumentRecord result = documentLibraryPort.findById(docId).orElse(initial);
        log.debug("uploadAndIngest() | return={}", result.status());
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractFullText(Path file) {
        log.debug("extractFullText() | file={}", file.getFileName());
        for (FileParserPort parser : parsers) {
            if (parser.supports(file)) {
                try {
                    List<String> blocks = parser.parse(file);
                    StringBuilder sb = new StringBuilder();
                    for (String block : blocks) {
                        sb.append(block).append("\n\n");
                    }
                    String result = sb.toString().trim();
                    log.debug("extractFullText() | return length={}", result.length());
                    return result;
                } catch (IOException e) {
                    log.warn("extractFullText() | parse failed: {}", e.getMessage());
                    return "";
                }
            }
        }
        log.debug("extractFullText() | return=empty (no parser)");
        return "";
    }

    private String pickSlug(CompilationReport report) {
        if (report.pagesCreated() != null && !report.pagesCreated().isEmpty()) {
            return report.pagesCreated().get(0);
        }
        if (report.pagesUpdated() != null && !report.pagesUpdated().isEmpty()) {
            return report.pagesUpdated().get(0);
        }
        return null;
    }
}
