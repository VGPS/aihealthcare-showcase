package com.wgblackmon.aihealthcare.application.service;

import com.wgblackmon.aihealthcare.domain.model.DocumentChunk;
import com.wgblackmon.aihealthcare.domain.model.DocumentIngestionResult;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentVectorPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FileParserPort;
import com.wgblackmon.aihealthcare.domain.service.DocumentIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentIngestionService}.
 *
 * <p>Uses a JUnit 5 {@code @TempDir} for real filesystem interaction (directory scanning,
 * file listing) while mocking {@link FileParserPort} and {@link DocumentVectorPort} to
 * avoid actual PDF/DOCX parsing or vector store calls.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-04-27
 * @updated 2026-08-25
 */
@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private FileParserPort parserPort;

    @Mock
    private DocumentVectorPort vectorPort;

    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionService(List.of(parserPort), vectorPort);
    }

    // -------------------------------------------------------------------------
    // Happy-path ingestion
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ingest() parses a supported file and stores its chunks")
    void ingest_supportedFile_parsesAndStoresChunks() throws IOException {
        Path file = Files.createFile(tempDir.resolve("guide.txt"));

        when(parserPort.supports(any(Path.class))).thenReturn(true);
        when(parserPort.parse(any(Path.class)))
                .thenReturn(List.of("Short paragraph one.", "Short paragraph two."));

        DocumentIngestionResult result = service.ingest(
                tempDir.toString(), "Clinical Guidelines", 1000);

        assertThat(result.filesProcessed()).isEqualTo(1);
        assertThat(result.chunksEmbedded()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        verify(vectorPort).store(anyList());
    }

    @Test
    @DisplayName("ingest() splits oversized text blocks into multiple chunks")
    void ingest_oversizedBlock_producesMultipleChunks() throws IOException {
        Files.createFile(tempDir.resolve("large.txt"));

        String longText = "word ".repeat(300); // ~1500 chars
        when(parserPort.supports(any(Path.class))).thenReturn(true);
        when(parserPort.parse(any(Path.class))).thenReturn(List.of(longText));

        DocumentIngestionResult result = service.ingest(
                tempDir.toString(), "Large Doc", 500);

        assertThat(result.chunksEmbedded()).isGreaterThan(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorPort).store(captor.capture());
        for (DocumentChunk chunk : captor.getValue()) {
            assertThat(chunk.content().length()).isLessThanOrEqualTo(500);
        }
    }

    @Test
    @DisplayName("ingest() skips unsupported files and does not call vectorPort")
    void ingest_unsupportedFiles_skipsAndReturnsZero() throws IOException {
        Files.createFile(tempDir.resolve("image.png"));
        when(parserPort.supports(any(Path.class))).thenReturn(false);

        DocumentIngestionResult result = service.ingest(
                tempDir.toString(), "Misc", 1000);

        assertThat(result.filesProcessed()).isEqualTo(0);
        assertThat(result.chunksEmbedded()).isEqualTo(0);
        verify(vectorPort, never()).store(anyList());
    }

    @Test
    @DisplayName("ingest() records failure for a file that throws during parsing")
    void ingest_parseThrows_recordsFailureAndContinues() throws IOException {
        Files.createFile(tempDir.resolve("broken.pdf"));
        Files.createFile(tempDir.resolve("good.txt"));

        when(parserPort.supports(any(Path.class))).thenReturn(true);
        when(parserPort.parse(any(Path.class)))
                .thenThrow(new IOException("corrupt file"))
                .thenReturn(List.of("Good content here."));

        DocumentIngestionResult result = service.ingest(
                tempDir.toString(), "Mixed", 1000);

        assertThat(result.failures()).hasSize(1);
        assertThat(result.filesProcessed()).isEqualTo(1);
        assertThat(result.chunksEmbedded()).isEqualTo(1);
    }

    @Test
    @DisplayName("ingest() does not call vectorPort when all files fail")
    void ingest_allFilesFail_doesNotCallVectorPort() throws IOException {
        Files.createFile(tempDir.resolve("bad.pdf"));
        when(parserPort.supports(any(Path.class))).thenReturn(true);
        when(parserPort.parse(any(Path.class))).thenThrow(new IOException("bad"));

        service.ingest(tempDir.toString(), "Failures", 1000);

        verify(vectorPort, never()).store(anyList());
    }

    @Test
    @DisplayName("ingest() sets correct sourceLabel on every chunk")
    void ingest_setsSourceLabelOnChunks() throws IOException {
        Files.createFile(tempDir.resolve("doc.txt"));
        when(parserPort.supports(any(Path.class))).thenReturn(true);
        when(parserPort.parse(any(Path.class))).thenReturn(List.of("Some text content."));

        service.ingest(tempDir.toString(), "My Label", 1000);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorPort).store(captor.capture());
        assertThat(captor.getValue().get(0).sourceLabel()).isEqualTo("My Label");
    }

    // -------------------------------------------------------------------------
    // Guard conditions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ingest() throws IllegalArgumentException for a non-existent directory")
    void ingest_nonExistentDirectory_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.ingest("/no/such/path", "label", 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("ingest() returns empty result for an empty directory")
    void ingest_emptyDirectory_returnsZeroCounts() {
        DocumentIngestionResult result = service.ingest(
                tempDir.toString(), "Empty", 1000);

        assertThat(result.filesProcessed()).isEqualTo(0);
        assertThat(result.chunksEmbedded()).isEqualTo(0);
        assertThat(result.failures()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // ingestFile() — single-file paragraph-aware chunking
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ingestFile() parses a supported file and stores paragraph chunks")
    void ingestFile_supportedFile_parsesAndStoresChunks() throws IOException {
        Path file = Files.createFile(tempDir.resolve("paper.txt"));

        when(parserPort.supports(any(Path.class))).thenReturn(true);
        when(parserPort.parse(any(Path.class)))
                .thenReturn(List.of("First paragraph.\n\nSecond paragraph.\n\nThird paragraph."));

        DocumentIngestionResult result = service.ingestFile(file, "Author Name");

        assertThat(result.filesProcessed()).isEqualTo(1);
        assertThat(result.chunksEmbedded()).isGreaterThan(0);
        assertThat(result.failures()).isEmpty();
        verify(vectorPort).store(anyList());
    }

    @Test
    @DisplayName("ingestFile() returns failure when no parser supports the file")
    void ingestFile_noParser_returnsFailure() throws IOException {
        Path file = Files.createFile(tempDir.resolve("unknown.xyz"));
        when(parserPort.supports(any(Path.class))).thenReturn(false);

        DocumentIngestionResult result = service.ingestFile(file, "Label");

        assertThat(result.filesProcessed()).isEqualTo(0);
        assertThat(result.chunksEmbedded()).isEqualTo(0);
        assertThat(result.failures()).hasSize(1);
        verify(vectorPort, never()).store(anyList());
    }

    @Test
    @DisplayName("ingestFile() returns failure when parser throws IOException")
    void ingestFile_parseThrows_returnsFailure() throws IOException {
        Path file = Files.createFile(tempDir.resolve("corrupt.pdf"));
        when(parserPort.supports(any(Path.class))).thenReturn(true);
        when(parserPort.parse(any(Path.class))).thenThrow(new IOException("corrupt"));

        DocumentIngestionResult result = service.ingestFile(file, "Label");

        assertThat(result.failures()).hasSize(1);
        assertThat(result.chunksEmbedded()).isEqualTo(0);
        verify(vectorPort, never()).store(anyList());
    }

    @Test
    @DisplayName("ingestFile() skips blank paragraphs in paragraph chunking")
    void ingestFile_blankParagraphsSkipped() throws IOException {
        Path file = Files.createFile(tempDir.resolve("spaced.txt"));
        when(parserPort.supports(any(Path.class))).thenReturn(true);
        // blocks with blank lines and a real paragraph
        when(parserPort.parse(any(Path.class)))
                .thenReturn(List.of("\n\n\n\nReal content here.\n\n  \n\n"));

        DocumentIngestionResult result = service.ingestFile(file, "Label");

        assertThat(result.chunksEmbedded()).isGreaterThan(0);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorPort).store(captor.capture());
        for (DocumentChunk chunk : captor.getValue()) {
            assertThat(chunk.content().isBlank()).isFalse();
        }
    }

    @Test
    @DisplayName("ingestFile() sets correct sourceLabel on paragraph chunks")
    void ingestFile_setsSourceLabel() throws IOException {
        Path file = Files.createFile(tempDir.resolve("study.txt"));
        when(parserPort.supports(any(Path.class))).thenReturn(true);
        when(parserPort.parse(any(Path.class))).thenReturn(List.of("Study findings text."));

        service.ingestFile(file, "Research Label");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorPort).store(captor.capture());
        assertThat(captor.getValue().get(0).sourceLabel()).isEqualTo("Research Label");
    }

    @Test
    @DisplayName("ingestFile() produces multiple chunks for large text with overlap")
    void ingestFile_largeText_producesMultipleChunksWithOverlap() throws IOException {
        Path file = Files.createFile(tempDir.resolve("big.txt"));
        when(parserPort.supports(any(Path.class))).thenReturn(true);
        // 5 paragraphs of ~220 chars each — after 3 paras (~660 chars), para 4 pushes over 800
        String para = "This paragraph contains enough words to ensure that the chunking algorithm is"
                + " properly exercised by accumulating text until the 800-character per-chunk threshold"
                + " is exceeded and a new chunk with overlap must be started for subsequent content.";
        String blocks = para + "\n\n" + para + "\n\n" + para + "\n\n" + para + "\n\n" + para;
        when(parserPort.parse(any(Path.class))).thenReturn(List.of(blocks));

        DocumentIngestionResult result = service.ingestFile(file, "Big Doc");

        assertThat(result.chunksEmbedded()).isGreaterThan(1);
    }
}
