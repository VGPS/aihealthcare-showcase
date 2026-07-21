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
 * @updated 2026-07-21
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

    @Test
    void findMostRecentSummaryDate_findsYesterdayFile() throws IOException {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String filename = yesterday.format(java.time.format.DateTimeFormatter.ofPattern("yyyy_MM_dd")) + ".html";
        Files.writeString(tempDir.resolve(filename), "<p>Yesterday</p>", StandardCharsets.UTF_8);

        Optional<LocalDate> result = adapter.findMostRecentSummaryDate();

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(yesterday);
    }

    @Test
    void findMostRecentSummaryDate_skipsToday_findsOlder() throws IOException {
        // Place a file from 3 days ago (not today, not yesterday)
        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        String filename = threeDaysAgo.format(java.time.format.DateTimeFormatter.ofPattern("yyyy_MM_dd")) + ".html";
        Files.writeString(tempDir.resolve(filename), "<p>Older</p>", StandardCharsets.UTF_8);

        Optional<LocalDate> result = adapter.findMostRecentSummaryDate();

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(threeDaysAgo);
    }

    @Test
    void findMostRecentSummaryDate_noFiles_returnsEmpty() {
        Optional<LocalDate> result = adapter.findMostRecentSummaryDate();

        assertThat(result).isEmpty();
    }
}
