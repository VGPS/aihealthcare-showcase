package com.wgblackmon.aihealthcare.domain.port.outbound;

import java.nio.file.Path;
import java.util.List;

/**
 * Outbound port — extract raw text blocks from a single document file.
 *
 * <p>Each implementation handles one file format (PDF, DOCX, plain text, etc.).
 * The {@link #supports} method lets the application layer dispatch to the correct
 * parser without knowing implementation details.
 *
 * <p>Returned text blocks are natural document units (e.g., one string per PDF
 * page, one per DOCX paragraph, or the full content for small text files).
 * The application service is responsible for further chunking if blocks exceed
 * the configured chunk size.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
public interface FileParserPort {

    /**
     * Returns {@code true} if this parser can handle the given file.
     *
     * @param file Path to the file; the check is based on file extension only.
     * @return {@code true} when this parser supports the file type.
     */
    boolean supports(Path file);

    /**
     * Extract text blocks from the given file.
     *
     * @param file Path to the document to parse; must exist and be readable.
     * @return Ordered list of text blocks extracted from the document; never null,
     *         may be empty if the document contains no extractable text.
     * @throws java.io.IOException if the file cannot be read or parsed.
     */
    List<String> parse(Path file) throws java.io.IOException;
}
