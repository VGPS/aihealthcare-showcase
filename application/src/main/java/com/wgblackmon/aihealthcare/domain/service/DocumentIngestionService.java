package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.DocumentChunk;
import com.wgblackmon.aihealthcare.domain.model.DocumentIngestionResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestDocumentsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentVectorPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FileParserPort;
import lombok.extern.slf4j.Slf4j;

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
 *   <li>Splits the extracted text into chunks (fixed-size for batch, paragraph-aware for single-file).</li>
 *   <li>Delegates all chunks to {@link DocumentVectorPort} for embedding and storage.</li>
 * </ol>
 *
 * <p>Per-file failures are captured and reported in the result rather than
 * aborting the entire run, so a single corrupt PDF does not prevent the rest
 * of the directory from being ingested.
 *
 * <p>This class carries no Spring annotations; {@code AppConfig} wires it as
 * a {@code @Bean} so the application layer remains framework-free.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-04-27
 * @updated 2026-08-25
 */
@Slf4j
public class DocumentIngestionService implements IngestDocumentsUseCase {

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
                List<DocumentChunk> fileChunks = buildChunks(blocks, file.getFileName().toString(),
                                                              sourceLabel, chunkSize);
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
            chunks = buildParagraphChunks(blocks, file.getFileName().toString(), sourceLabel);
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
     * Splits a list of text blocks into {@link DocumentChunk}s, ensuring no chunk
     * exceeds {@code chunkSize} characters.  Blocks shorter than {@code chunkSize}
     * are emitted as a single chunk.  Blocks longer than {@code chunkSize} are
     * split on whitespace boundaries where possible.
     */
    private List<DocumentChunk> buildChunks(List<String> blocks,
                                             String sourceFile,
                                             String sourceLabel,
                                             int chunkSize) {
        log.debug("buildChunks() | sourceFile={}, blocks={}, chunkSize={}",
                  sourceFile, blocks.size(), chunkSize);
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.length() <= chunkSize) {
                chunks.add(new DocumentChunk(
                        UUID.randomUUID().toString(),
                        sourceFile,
                        chunkIndex++,
                        trimmed,
                        sourceLabel));
            } else {
                // Split oversized block into chunkSize-character pieces
                int start = 0;
                while (start < trimmed.length()) {
                    int end = Math.min(start + chunkSize, trimmed.length());
                    // Try to break at a whitespace boundary
                    if (end < trimmed.length()) {
                        int lastSpace = trimmed.lastIndexOf(' ', end);
                        if (lastSpace > start) {
                            end = lastSpace;
                        }
                    }
                    String piece = trimmed.substring(start, end).trim();
                    if (!piece.isBlank()) {
                        chunks.add(new DocumentChunk(
                                UUID.randomUUID().toString(),
                                sourceFile,
                                chunkIndex++,
                                piece,
                                sourceLabel));
                    }
                    start = end;
                    // Skip leading whitespace for the next piece
                    while (start < trimmed.length() && trimmed.charAt(start) == ' ') {
                        start++;
                    }
                }
            }
        }
        log.debug("buildChunks() | return={} chunks", chunks.size());
        return chunks;
    }

    /**
     * Paragraph-aware chunking with 150-char overlap for single-file uploads.
     *
     * <p>Algorithm: split on double-newline → aggregate short paragraphs into
     * the current chunk (up to 800 chars) → emit chunk → prepend 150-char tail
     * of the emitted chunk as the overlap prefix for the next chunk.
     */
    List<DocumentChunk> buildParagraphChunks(List<String> blocks,
                                             String sourceFile,
                                             String sourceLabel) {
        log.debug("buildParagraphChunks() | sourceFile={}, blocks={}", sourceFile, blocks.size());

        final int MAX_CHUNK   = 800;
        final int OVERLAP_LEN = 150;

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
            if (current.length() == 0 && !overlap.isBlank()) {
                current.append(overlap).append(" ");
            }
            if (current.length() + para.length() > MAX_CHUNK && current.length() > 0) {
                String content = current.toString().trim();
                if (!content.isBlank()) {
                    chunks.add(new DocumentChunk(
                            UUID.randomUUID().toString(), sourceFile, chunkIndex++, content, sourceLabel));
                    int overlapStart = Math.max(0, content.length() - OVERLAP_LEN);
                    overlap = content.substring(overlapStart);
                }
                current = new StringBuilder();
                current.append(overlap).append(" ");
            }
            current.append(para).append("\n\n");
        }

        String remainder = current.toString().trim();
        if (!remainder.isBlank()) {
            chunks.add(new DocumentChunk(
                    UUID.randomUUID().toString(), sourceFile, chunkIndex, remainder, sourceLabel));
        }

        log.debug("buildParagraphChunks() | return={} chunks", chunks.size());
        return chunks;
    }
}
