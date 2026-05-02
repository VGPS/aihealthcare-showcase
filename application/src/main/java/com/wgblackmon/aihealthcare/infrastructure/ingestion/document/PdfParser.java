package com.wgblackmon.aihealthcare.infrastructure.ingestion.document;

import com.wgblackmon.aihealthcare.domain.port.outbound.FileParserPort;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link FileParserPort} implementation for PDF files using Apache PDFBox 3.x.
 *
 * <p>Extracts text page-by-page, returning one non-blank string per page.
 * Page-level granularity gives the chunking layer a natural unit to work with —
 * pages that are already short enough are stored as a single chunk, while
 * dense pages get split further by the application service.
 *
 * <p>PDFBox 3.x uses {@link Loader#loadPDF} instead of the deprecated
 * {@code PDDocument.load()} from the 2.x API.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
@Slf4j
@Component
public class PdfParser implements FileParserPort {

    @Override
    public boolean supports(Path file) {
        log.debug("supports() | file={}", file.getFileName());
        boolean result = file.getFileName().toString().toLowerCase().endsWith(".pdf");
        log.debug("supports() | return={}", result);
        return result;
    }

    @Override
    public List<String> parse(Path file) throws IOException {
        log.debug("parse() | file={}", file.getFileName());
        List<String> pages = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            int pageCount = document.getNumberOfPages();
            log.debug("parse() | pageCount={}", pageCount);

            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) {
                    pages.add(text.trim());
                }
            }
        }

        log.debug("parse() | return={} non-blank pages", pages.size());
        return pages;
    }
}
