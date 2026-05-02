package com.wgblackmon.aihealthcare.infrastructure.ingestion.document;

import com.wgblackmon.aihealthcare.domain.port.outbound.FileParserPort;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link FileParserPort} implementation for Microsoft Word files (.docx) using
 * Apache POI.
 *
 * <p>Iterates over all paragraphs in the document and returns each non-blank
 * paragraph as a separate text block.  This paragraph-level granularity maps
 * naturally to the chunking strategy in
 * {@link com.wgblackmon.aihealthcare.domain.service.DocumentIngestionService}:
 * short paragraphs may be stored as individual chunks, while long ones are
 * split further.
 *
 * <p>Only {@code .docx} (OOXML) format is supported; legacy {@code .doc}
 * (OLE2 / HSSF) files are not handled.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
@Slf4j
@Component
public class DocxParser implements FileParserPort {

    @Override
    public boolean supports(Path file) {
        log.debug("supports() | file={}", file.getFileName());
        boolean result = file.getFileName().toString().toLowerCase().endsWith(".docx");
        log.debug("supports() | return={}", result);
        return result;
    }

    @Override
    public List<String> parse(Path file) throws IOException {
        log.debug("parse() | file={}", file.getFileName());
        List<String> paragraphs = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(in)) {

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    paragraphs.add(text.trim());
                }
            }
        }

        log.debug("parse() | return={} non-blank paragraphs", paragraphs.size());
        return paragraphs;
    }
}
