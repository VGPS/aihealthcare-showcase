package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;
import com.wgblackmon.aihealthcare.domain.model.DocumentIngestionResult;
import com.wgblackmon.aihealthcare.domain.model.DocumentRecord;
import com.wgblackmon.aihealthcare.domain.model.DocumentStatus;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestDocumentsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentLibraryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FileParserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.KnowledgeCompilationPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentUploadService}, focused on the failure-visibility
 * fixes: zero-chunk extraction now fails loudly instead of silently reporting
 * INDEXED, wiki-compilation exceptions are retained as a visible error message
 * instead of being swallowed, and the caller-supplied topic replaces the old
 * hardcoded "Document" topic on the compiled wiki article.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-25
 * @updated 2026-08-25
 */
class DocumentUploadServiceTest {

    private final IngestDocumentsUseCase ingestUseCase = mock(IngestDocumentsUseCase.class);
    private final DocumentLibraryPort documentLibraryPort = mock(DocumentLibraryPort.class);
    private final KnowledgeCompilationPort knowledgeCompilationPort = mock(KnowledgeCompilationPort.class);
    private final FileParserPort parser = mock(FileParserPort.class);

    private final Path file = Paths.get("study.pdf");

    @Test
    void uploadAndIngest_zeroChunksExtracted_marksFailedInsteadOfSilentIndexed() throws Exception {
        DocumentUploadService service = new DocumentUploadService(
                ingestUseCase, documentLibraryPort, knowledgeCompilationPort, List.of(parser));

        when(ingestUseCase.ingestFile(any(), anyString()))
                .thenReturn(new DocumentIngestionResult(1, 0, List.of()));
        when(documentLibraryPort.findById(anyString())).thenAnswer(invocation -> {
            String docId = invocation.getArgument(0);
            return Optional.of(new DocumentRecord(docId, "scanned.pdf", "Dr Smith", "AI Healthcare Legal",
                    null, null, Instant.now(), 0, null, DocumentStatus.FAILED,
                    "No extractable text found in 'scanned.pdf' — it may be a scanned/image-only PDF, empty, or corrupted."));
        });

        DocumentRecord result = service.uploadAndIngest(file, "scanned.pdf", "Dr Smith", "AI Healthcare Legal");

        verify(documentLibraryPort).updateStatus(
                eq(result.docId()), eq(DocumentStatus.FAILED), eq(0), isNull(), anyString());
        assertThat(result.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(result.errorMessage()).contains("No extractable text");
    }

    @Test
    void uploadAndIngest_wikiCompilationThrows_keepsIndexedButRecordsErrorMessage() throws Exception {
        DocumentUploadService service = new DocumentUploadService(
                ingestUseCase, documentLibraryPort, knowledgeCompilationPort, List.of(parser));

        when(ingestUseCase.ingestFile(any(), anyString()))
                .thenReturn(new DocumentIngestionResult(1, 5, List.of()));
        when(parser.supports(file)).thenReturn(true);
        when(parser.parse(file)).thenReturn(List.of("Some extracted body text."));
        when(knowledgeCompilationPort.compileNewSources(any()))
                .thenThrow(new RuntimeException("LLM timeout"));
        when(documentLibraryPort.findById(anyString())).thenReturn(Optional.empty());

        DocumentRecord result = service.uploadAndIngest(file, "study.pdf", "Dr Smith", "AI Healthcare Legal");

        verify(documentLibraryPort).updateStatus(
                eq(result.docId()), eq(DocumentStatus.INDEXED), eq(5), isNull(),
                eq("Wiki compilation failed: LLM timeout"));
    }

    @Test
    void uploadAndIngest_wikiCompilationSucceeds_usesCallerSuppliedTopicNotHardcodedDocument() throws Exception {
        DocumentUploadService service = new DocumentUploadService(
                ingestUseCase, documentLibraryPort, knowledgeCompilationPort, List.of(parser));

        when(ingestUseCase.ingestFile(any(), anyString()))
                .thenReturn(new DocumentIngestionResult(1, 5, List.of()));
        when(parser.supports(file)).thenReturn(true);
        when(parser.parse(file)).thenReturn(List.of("Some extracted body text."));
        when(knowledgeCompilationPort.compileNewSources(any())).thenReturn(new CompilationReport(
                Instant.now(), Instant.now(), 1, List.of("ai-healthcare-legal-update"), List.of(), List.of(), List.of()));
        when(documentLibraryPort.findById(anyString())).thenReturn(Optional.empty());

        service.uploadAndIngest(file, "study.pdf", "Dr Smith", "AI Healthcare Legal");

        org.mockito.ArgumentCaptor<List<NewsArticle>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(knowledgeCompilationPort).compileNewSources(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).topic()).isEqualTo("AI Healthcare Legal");
    }
}
