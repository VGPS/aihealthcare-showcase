package com.wgblackmon.aihealthcare.infrastructure.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NotebookLMSummaryAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
class NotebookLMSummaryAdapterTest {

    @TempDir
    Path tempDir;

    private NotebookLMSummaryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NotebookLMSummaryAdapter(tempDir.toString());
    }

    @Test
    void getHtmlSummary_fileExists_returnsContent() throws IOException {
        LocalDate date = LocalDate.of(2026, 7, 20);
        String htmlContent = "<html><body>Test summary</body></html>";
        Files.writeString(tempDir.resolve("2026_07_20.html"), htmlContent, StandardCharsets.UTF_8);

        Optional<String> result = adapter.getHtmlSummary(date);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(htmlContent);
    }

    @Test
    void getHtmlSummary_fileDoesNotExist_returnsEmpty() {
        LocalDate date = LocalDate.of(2026, 7, 20);

        Optional<String> result = adapter.getHtmlSummary(date);

        assertThat(result).isEmpty();
    }

    @Test
    void getTextSummary_fileExists_returnsContent() throws IOException {
        LocalDate date = LocalDate.of(2026, 7, 20);
        String textContent = "Plain text summary content";
        Files.writeString(tempDir.resolve("2026_07_20.txt"), textContent, StandardCharsets.UTF_8);

        Optional<String> result = adapter.getTextSummary(date);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(textContent);
    }
}
