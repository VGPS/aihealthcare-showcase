package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.DocumentChunk;
import com.wgblackmon.aihealthcare.domain.model.DocumentIngestionResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestDocumentsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentVectorPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FileParserPort;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application service that implements {@link IngestDocumentsUseCase}.
 *
 * <p>Orchestrates the full document-to-vector pipeline:
 * <ol>
 *   <li>Validates and scans the target directory for supported files.</li>
 *   <li>Dispatches each file to the appropriate {@link FileParserPort} implementation.</li>
 *   <li>Splits the extracted text into paragraph-aware chunks with overlap.</li>
 *   <li>Delegates all chunks to {@link DocumentVectorPort} for embedding and storage.</li>
 * </ol>
 *
 * <p>Both entry points ({@code ingest} for batch directory, {@code ingestFile} for
 * single-file upload) now use the same {@link #buildParagraphChunks} algorithm, which
 * re-splits on double-newline, accumulates across blocks, and prepends a
 * {@link #PARAGRAPH_OVERLAP}-char tail from the previous chunk onto each new one.
 * The {@code chunkSize} parameter on {@code ingest} still controls the emission
 * threshold; the overlap is always {@link #PARAGRAPH_OVERLAP} regardless.
 *
 * <p>Per-file failures are captured and reported in the result rather than
 * aborting the entire run, so a single corrupt file does not prevent the rest
 * of the directory from being ingested.
 *
 * <p>This class carries no Spring annotations; {@code AppConfig} wires it as
 * a {@code @Bean} so the application layer remains framework-free.
 *
 * @author  Bill Blackmon
 * @version 1.4
 * @since   2026-04-27
 * @updated 2026-11-23
 */
public class DocumentIngestionService implements IngestDocumentsUseCase {

    private static final DomainLogger log = new DomainLogger(DocumentIngestionService.class);

    public static final int PARAGRAPH_MAX_CHUNK = 800;
    public static final int PARAGRAPH_OVERLAP   = 150;

    private final List<FileParserPort> parsers;
    private final DocumentVectorPort   vectorPort;

    public DocumentIngestionService(List<FileParserPort> parsers,
                                     DocumentVectorPort vectorPort) {
        log.debug("DocumentIngestionService() | parserCount={}, vectorPort={}",
                  parsers.size(), vectorPort);
        this.parsers    = parsers;
        this.vectorPort = vectorPort;
    }

    // -------------------------------------------------------------------------
    // IngestDocumentsUseCase
    // -------------------------------------------------------------------------

    @Override
    public DocumentIngestionResult ingest(String directory, String sourceLabel, int chunkSize) {
        log.debug("ingest() | directory={}, sourceLabel={}, chunkSize={}",
                  directory, sourceLabel, chunkSize);

        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException(
                    "Directory does not exist or is not a directory: " + directory);
        }

        List<Path> files = listSupportedFiles(dirPath);
        log.info("ingest() | Found {} supported file(s) in {}", files.size(), directory);

        int filesProcessed = 0;
        int chunksEmbedded = 0;
        List<String> failures = new ArrayList<>();
        List<DocumentChunk> allChunks = new ArrayList<>();

        for (Path file : files) {
            FileParserPort parser = findParser(file);
            if (parser == null) {
                log.debug("ingest() | No parser found for file={}, skipping", file.getFileName());
                continue;
            }
            try {
                List<String> blocks = parser.parse(file);
                // Uses the same paragraph-aware chunker as ingestFile(); chunkSize controls
                // the emission threshold, overlap is always PARAGRAPH_OVERLAP.
                List<DocumentChunk> fileChunks = buildParagraphChunks(blocks,
                        file.getFileName().toString(), sourceLabel, chunkSize);
                for (DocumentChunk chunk : fileChunks) {
                    allChunks.add(chunk);
                }
                filesProcessed++;
                log.debug("ingest() | Parsed file={}, blocks={}, chunks={}",
                          file.getFileName(), blocks.size(), fileChunks.size());
            } catch (IOException e) {
                String msg = file.getFileName() + ": " + e.getMessage();
                failures.add(msg);
                log.warn("ingest() | Failed to parse file={}: {}", file.getFileName(), e.getMessage());
            }
        }

        if (!allChunks.isEmpty()) {
            vectorPort.store(allChunks);
            chunksEmbedded = allChunks.size();
        }

        DocumentIngestionResult result = new DocumentIngestionResult(
                filesProcessed, chunksEmbedded, failures);
        log.info("ingest() | Complete: filesProcessed={}, chunksEmbedded={}, failures={}",
                 filesProcessed, chunksEmbedded, failures.size());
        log.debug("ingest() | return={}", result);
        return result;
    }

    // -------------------------------------------------------------------------
    // IngestDocumentsUseCase — single-file overload with paragraph-aware chunking
    // -------------------------------------------------------------------------

    @Override
    public DocumentIngestionResult ingestFile(java.nio.file.Path file, String sourceLabel) {
        log.debug("ingestFile() | file={}, sourceLabel={}", file.getFileName(), sourceLabel);

        FileParserPort parser = findParser(file);
        if (parser == null) {
            DocumentIngestionResult result = new DocumentIngestionResult(
                    0, 0, List.of("No parser available for: " + file.getFileName()));
            log.debug("ingestFile() | return={}", result);
            return result;
        }

        List<DocumentChunk> chunks;
        try {
            List<String> blocks = parser.parse(file);
            chunks = buildParagraphChunks(blocks, file.getFileName().toString(), sourceLabel,
                    PARAGRAPH_MAX_CHUNK);
        } catch (java.io.IOException e) {
            DocumentIngestionResult result = new DocumentIngestionResult(
                    0, 0, List.of(file.getFileName() + ": " + e.getMessage()));
            log.warn("ingestFile() | parse failed: {}", e.getMessage());
            log.debug("ingestFile() | return={}", result);
            return result;
        }

        if (!chunks.isEmpty()) {
            vectorPort.store(chunks);
        }

        DocumentIngestionResult result = new DocumentIngestionResult(1, chunks.size(), List.of());
        log.info("ingestFile() | Complete: file={}, chunksEmbedded={}", file.getFileName(), chunks.size());
        log.debug("ingestFile() | return={}", result);
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Scans {@code dir} for files with supported extensions (.pdf, .docx, .txt, .md).
     * Subdirectories are not traversed.
     */
    private List<Path> listSupportedFiles(Path dir) {
        log.debug("listSupportedFiles() | dir={}", dir);
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry) && isSupported(entry)) {
                    result.add(entry);
                }
            }
        } catch (IOException e) {
            log.warn("listSupportedFiles() | Could not list directory {}: {}", dir, e.getMessage());
        }
        log.debug("listSupportedFiles() | return={} files", result.size());
        return result;
    }

    /**
     * Returns {@code true} if any registered parser supports the given file.
     */
    private boolean isSupported(Path file) {
        for (FileParserPort parser : parsers) {
            if (parser.supports(file)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the first parser that supports the given file, or {@code null} if none does.
     */
    private FileParserPort findParser(Path file) {
        log.debug("findParser() | file={}", file.getFileName());
        for (FileParserPort parser : parsers) {
            if (parser.supports(file)) {
                log.debug("findParser() | return={}", parser.getClass().getSimpleName());
                return parser;
            }
        }
        log.debug("findParser() | return=null");
        return null;
    }

    /**
     * Paragraph-aware chunking with {@link #PARAGRAPH_OVERLAP}-char context overlap.
     *
     * <p>Used by both entry points — batch directory ingest ({@code ingest()}) and
     * single-file upload ({@code ingestFile()}).  The only difference between the two
     * callers is the {@code maxChunk} value: {@code ingest()} passes the caller-supplied
     * {@code chunkSize}; {@code ingestFile()} passes {@link #PARAGRAPH_MAX_CHUNK}.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Re-split each parser block on {@code "\n\n"} to obtain individual paragraphs.</li>
     *   <li>Pre-split any paragraph exceeding {@code maxChunk} at whitespace boundaries
     *       ({@link #splitAtBoundary}) so no raw piece ever exceeds {@code maxChunk}.</li>
     *   <li>Accumulate pieces into a growing buffer; emit when the next piece would push
     *       the buffer past {@code maxChunk}.</li>
     *   <li>Prepend the last {@link #PARAGRAPH_OVERLAP} chars of the emitted chunk as a
     *       context prefix onto the next chunk.</li>
     * </ol>
     *
     * <p><b>Stored chunk size:</b> due to the overlap prefix, a stored chunk can be up to
     * {@code maxChunk + PARAGRAPH_OVERLAP + 1} characters — slightly larger than
     * {@code maxChunk} alone.  This is intentional: the overlap keeps cross-boundary
     * context visible to the retrieval model.
     *
     * @param blocks     parser output — one entry per parsed block
     * @param sourceFile file name, stored as chunk metadata
     * @param sourceLabel label for the document collection, stored as chunk metadata
     * @param maxChunk   emission threshold in characters
     */
    List<DocumentChunk> buildParagraphChunks(List<String> blocks,
                                             String sourceFile,
                                             String sourceLabel,
                                             int maxChunk) {
        log.debug("buildParagraphChunks() | sourceFile={}, blocks={}, maxChunk={}",
                sourceFile, blocks.size(), maxChunk);

        List<String> paragraphs = new ArrayList<>();
        for (String block : blocks) {
            String[] parts = block.split("\n\n");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    paragraphs.add(trimmed);
                }
            }
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        String overlap = "";
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            for (String piece : splitAtBoundary(para, maxChunk)) {
                if (current.length() == 0 && !overlap.isBlank()) {
                    current.append(overlap).append(" ");
                }
                if (current.length() + piece.length() > maxChunk && current.length() > 0) {
                    String content = current.toString().trim();
                    if (!content.isBlank()) {
                        chunks.add(new DocumentChunk(
                                UUID.randomUUID().toString(), sourceFile, chunkIndex++, content, sourceLabel));
                        int overlapStart = Math.max(0, content.length() - PARAGRAPH_OVERLAP);
                        overlap = content.substring(overlapStart);
                    }
                    current = new StringBuilder();
                    current.append(overlap).append(" ");
                }
                current.append(piece).append("\n\n");
            }
        }

        String remainder = current.toString().trim();
        if (!remainder.isBlank()) {
            chunks.add(new DocumentChunk(
                    UUID.randomUUID().toString(), sourceFile, chunkIndex, remainder, sourceLabel));
        }

        log.debug("buildParagraphChunks() | return={} chunks", chunks.size());
        return chunks;
    }

    /**
     * Splits {@code text} into pieces each no longer than {@code maxLen} chars,
     * breaking at whitespace boundaries where possible.  Returns a single-element
     * list when {@code text.length() <= maxLen}.
     */
    private List<String> splitAtBoundary(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return List.of(text);
        }
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isBlank()) {
                pieces.add(piece);
            }
            start = end;
            while (start < text.length() && text.charAt(start) == ' ') {
                start++;
            }
        }
        return pieces;
    }
}
