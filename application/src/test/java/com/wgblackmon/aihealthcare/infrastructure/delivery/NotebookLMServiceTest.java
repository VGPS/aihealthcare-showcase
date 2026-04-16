package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link NotebookLMService}.
 *
 * <p>Uses JUnit 5's {@link TempDir} to provide an isolated writable directory for
 * each test — no production filesystem paths are touched.  The service is
 * constructed directly (no Spring context) by passing the temp-dir path as the
 * {@code exportDirectory} constructor argument, exercising the same code path
 * that the {@code @Value} injection follows at runtime.
 *
 * <p>Tests cover: file creation, NotebookLM tag structure, directory auto-creation,
 * filename sanitization, empty article list, body-text fallback to URL, and
 * blank-title validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
class NotebookLMServiceTest {

    @TempDir
    Path tempDir;

    private NotebookLMService service;

    // --- shared fixtures ---

    private static final String TITLE = "AI in Healthcare Weekly";

    private static NewsArticle article(String id, String topic, String articleTitle, String body) {
        return new NewsArticle(
                id,
                articleTitle,
                URI.create("https://example.com/" + id),
                body,
                topic,
                null, null, null, null,
                0.5,
                null
        );
    }

    @BeforeEach
    void setUp() {
        service = new NotebookLMService(tempDir.toString());
    }

    // -------------------------------------------------------------------------
    // export() — file creation and naming
    // -------------------------------------------------------------------------

    @Test
    void export_createsFileInConfiguredDirectory() throws IOException {
        List<NewsArticle> articles = List.of(
                article("a-001", "AI Diagnostics", "AI Detects Cancer", "Body text here.")
        );

        Path result = service.export(TITLE, articles);

        assertThat(result).exists();
        assertThat(result.getParent()).isEqualTo(tempDir);
    }

    @Test
    void export_filenameMatchesSanitizedTitle() throws IOException {
        Path result = service.export(TITLE, List.of());

        assertThat(result.getFileName().toString()).isEqualTo("AI in Healthcare Weekly.txt");
    }

    @Test
    void export_specialCharsInTitleAreSanitized() throws IOException {
        Path result = service.export("AI/Healthcare: Weekly*Report?", List.of());

        assertThat(result.getFileName().toString())
                .isEqualTo("AI_Healthcare_ Weekly_Report_.txt");
    }

    // -------------------------------------------------------------------------
    // export() — NotebookLM content format
    // -------------------------------------------------------------------------

    @Test
    void export_contentStartsWithTitleTag() throws IOException {
        Path result = service.export(TITLE, List.of());

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).startsWith("<title>" + TITLE + "</title>");
    }

    @Test
    void export_eachArticleHasTopicTitleBodyTags() throws IOException {
        NewsArticle a = article("a-001", "AI Diagnostics", "AI Detects Cancer", "Body text.");
        Path result = service.export(TITLE, List.of(a));

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).contains("<topic>AI Diagnostics</topic>");
        assertThat(content).contains("<title>AI Detects Cancer</title>");
        assertThat(content).contains("<body>Body text.</body>");
    }

    @Test
    void export_multipleArticlesAllAppearInFile() throws IOException {
        List<NewsArticle> articles = List.of(
                article("a-001", "AI Diagnostics", "Article One", "Body one."),
                article("a-002", "ML Research",    "Article Two", "Body two.")
        );

        Path result = service.export(TITLE, articles);

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).contains("<topic>AI Diagnostics</topic>");
        assertThat(content).contains("<topic>ML Research</topic>");
        assertThat(content).contains("<title>Article One</title>");
        assertThat(content).contains("<title>Article Two</title>");
    }

    @Test
    void export_emptyArticleList_writesOnlyTitleTag() throws IOException {
        Path result = service.export(TITLE, List.of());

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).contains("<title>" + TITLE + "</title>");
        assertThat(content).doesNotContain("<topic>");
        assertThat(content).doesNotContain("<body>");
    }

    // -------------------------------------------------------------------------
    // export() — body-text fallback
    // -------------------------------------------------------------------------

    @Test
    void export_blankBodyText_fallsBackToUrl() throws IOException {
        NewsArticle a = article("a-001", "AI Diagnostics", "Title Only", "");
        Path result = service.export(TITLE, List.of(a));

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).contains("<body>https://example.com/a-001</body>");
    }

    // -------------------------------------------------------------------------
    // export() — directory auto-creation
    // -------------------------------------------------------------------------

    @Test
    void export_directoryDoesNotExist_isCreatedAutomatically() throws IOException {
        Path subDir = tempDir.resolve("new-sub-dir");
        NotebookLMService svc = new NotebookLMService(subDir.toString());

        assertThat(subDir).doesNotExist();

        svc.export(TITLE, List.of());

        assertThat(subDir).exists().isDirectory();
    }

    // -------------------------------------------------------------------------
    // export() — validation
    // -------------------------------------------------------------------------

    @Test
    void export_blankTitle_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.export("  ", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void export_nullTitle_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.export(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }
}
