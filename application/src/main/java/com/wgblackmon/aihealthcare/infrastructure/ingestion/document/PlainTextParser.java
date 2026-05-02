package com.wgblackmon.aihealthcare.infrastructure.ingestion.document;

import com.wgblackmon.aihealthcare.domain.port.outbound.FileParserPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@link FileParserPort} implementation for plain-text and Markdown files (.txt, .md).
 *
 * <p>Reads the entire file as UTF-8 and returns its content as a single text block.
 * The {@link com.wgblackmon.aihealthcare.domain.service.DocumentIngestionService}
 * is responsible for splitting the block into chunks if it exceeds the configured
 * chunk size.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
@Slf4j
@Component
public class PlainTextParser implements FileParserPort {

    @Override
    public boolean supports(Path file) {
        log.debug("supports() | file={}", file.getFileName());
        String name = file.getFileName().toString().toLowerCase();
        boolean result = name.endsWith(".txt") || name.endsWith(".md");
        log.debug("supports() | return={}", result);
        return result;
    }

    @Override
    public List<String> parse(Path file) throws IOException {
        log.debug("parse() | file={}", file.getFileName());
        String content = Files.readString(file);
        log.debug("parse() | return=1 block, length={} chars", content.length());
        return List.of(content);
    }
}
