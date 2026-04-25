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
 * <p>Tests cover both outputs: the summary document (historical record) and
 * individual per-article files (NotebookLM indexing), including title-based
 * dedup, tag structure, directory auto-creation, filename sanitization,
 * empty article list, body-text fallback to URL, and blank-title validation.
 *
 * @author  Bill Blackmon
 * @version 3.0
 * @since   2026-04-13
 * @updated 2026-04-25
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
                null, null, "TestSource", null,
                0.5,
                null
        );
    }

    @BeforeEach
    void setUp() {
        service = new NotebookLMService(tempDir.toString());
    }

    // =========================================================================
    // Summary document tests (historical record)
    // =========================================================================

    @Test
    void export_returnsSummaryFilePath() throws IOException {
        Path result = service.export(TITLE, List.of());

        assertThat(result).exists();
        assertThat(result.getParent()).isEqualTo(tempDir);
    }

    @Test
    void export_summaryFilenameMatchesSanitizedTitle() throws IOException {
        Path result = service.export(TITLE, List.of());

        assertThat(result.getFileName().toString()).isEqualTo("AI in Healthcare Weekly.txt");
    }

    @Test
    void export_specialCharsInTitleAreSanitized() throws IOException {
        Path result = service.export("AI/Healthcare: Weekly*Report?", List.of());

        assertThat(result.getFileName().toString())
                .isEqualTo("AI_Healthcare_ Weekly_Report_.txt");
    }

    @Test
    void export_summaryContentStartsWithTitleTag() throws IOException {
        Path result = service.export(TITLE, List.of());

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).startsWith("<title>" + TITLE + "</title>");
    }

    @Test
    void export_summaryContainsAllArticleTags() throws IOException {
        NewsArticle a = article("a-001", "AI Diagnostics", "AI Detects Cancer", "Body text.");
        Path result = service.export(TITLE, List.of(a));

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).contains("<source>TestSource</source>");
        assertThat(content).contains("<topic>AI Diagnostics</topic>");
        assertThat(content).contains("<title>AI Detects Cancer</title>");
        assertThat(content).contains("<url>https://example.com/a-001</url>");
        assertThat(content).contains("<body>Body text.</body>");
    }

    @Test
    void export_summaryContainsMultipleArticles() throws IOException {
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
    void export_emptyArticleList_summaryContainsOnlyTitleTag() throws IOException {
        Path result = service.export(TITLE, List.of());

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).contains("<title>" + TITLE + "</title>");
        assertThat(content).doesNotContain("<topic>");
        assertThat(content).doesNotContain("<body>");
    }

    // =========================================================================
    // Individual article file tests (NotebookLM indexing)
    // =========================================================================

    @Test
    void export_createsIndividualFilePerArticle() throws IOException {
        List<NewsArticle> articles = List.of(
                article("a-001", "AI Diagnostics", "AI Detects Cancer", "Body text here."),
                article("a-002", "ML Research", "ML in Clinical Trials", "Body two.")
        );

        service.export(TITLE, articles);

        assertThat(tempDir.resolve("AI Detects Cancer.txt")).exists();
        assertThat(tempDir.resolve("ML in Clinical Trials.txt")).exists();
    }

    @Test
    void export_individualFileHasSourceTopicTitleUrlBodyTags() throws IOException {
        NewsArticle a = article("a-001", "AI Diagnostics", "AI Detects Cancer", "Body text.");
        service.export(TITLE, List.of(a));

        String content = Files.readString(tempDir.resolve("AI Detects Cancer.txt"), StandardCharsets.UTF_8);
        assertThat(content).contains("<source>TestSource</source>");
        assertThat(content).contains("<topic>AI Diagnostics</topic>");
        assertThat(content).contains("<title>AI Detects Cancer</title>");
        assertThat(content).contains("<url>https://example.com/a-001</url>");
        assertThat(content).contains("<body>Body text.</body>");
    }

    @Test
    void export_individualFileDoesNotContainSummaryTitle() throws IOException {
        service.export(TITLE, List.of(
                article("a-001", "AI", "Test Article", "Body.")
        ));

        String content = Files.readString(tempDir.resolve("Test Article.txt"), StandardCharsets.UTF_8);
        assertThat(content).doesNotContain(TITLE);
    }

    @Test
    void export_emptyArticleList_noIndividualFilesWritten() throws IOException {
        service.export(TITLE, List.of());

        // Only the summary file should exist
        long txtFileCount = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().endsWith(".txt"))
                .count();
        assertThat(txtFileCount).isEqualTo(1); // just the summary
    }

    // =========================================================================
    // Individual file — title-based dedup
    // =========================================================================

    @Test
    void export_duplicateTitlesInBatch_onlyFirstIndividualFileIsWritten() throws IOException {
        List<NewsArticle> articles = List.of(
                article("a-001", "AI", "Same Title", "First body."),
                article("a-002", "ML", "Same Title", "Second body.")
        );

        service.export(TITLE, articles);

        String content = Files.readString(tempDir.resolve("Same Title.txt"), StandardCharsets.UTF_8);
        assertThat(content).contains("First body.");
    }

    @Test
    void export_duplicateTitlesCaseInsensitive_onlyFirstIndividualFileIsWritten() throws IOException {
        List<NewsArticle> articles = List.of(
                article("a-001", "AI", "AI Cancer Detection", "First."),
                article("a-002", "ML", "ai cancer detection", "Second.")
        );

        service.export(TITLE, articles);

        // Only one individual file (plus the summary)
        long txtFileCount = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().endsWith(".txt"))
                .count();
        assertThat(txtFileCount).isEqualTo(2); // summary + 1 individual
    }

    @Test
    void export_individualFileAlreadyOnDisk_isSkipped() throws IOException {
        // Pre-create a file that matches an article title
        Files.writeString(tempDir.resolve("Existing Article.txt"), "old content", StandardCharsets.UTF_8);

        service.export(TITLE, List.of(
                article("a-001", "AI", "Existing Article", "New body.")
        ));

        // Should not overwrite the pre-existing individual file
        String content = Files.readString(tempDir.resolve("Existing Article.txt"), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("old content");
    }

    @Test
    void export_summaryStillContainsDuplicatedArticles() throws IOException {
        List<NewsArticle> articles = List.of(
                article("a-001", "AI", "Same Title", "First body."),
                article("a-002", "ML", "Same Title", "Second body.")
        );

        Path summary = service.export(TITLE, articles);

        // Summary document should contain BOTH articles regardless of dedup
        String content = Files.readString(summary, StandardCharsets.UTF_8);
        assertThat(content).contains("First body.");
        assertThat(content).contains("Second body.");
    }

    // =========================================================================
    // Body-text fallback
    // =========================================================================

    @Test
    void export_blankBodyText_fallsBackToUrl() throws IOException {
        NewsArticle a = article("a-001", "AI Diagnostics", "Title Only", "");
        Path result = service.export(TITLE, List.of(a));

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).contains("<body>https://example.com/a-001</body>");
    }

    // =========================================================================
    // Directory auto-creation
    // =========================================================================

    @Test
    void export_directoryDoesNotExist_isCreatedAutomatically() throws IOException {
        Path subDir = tempDir.resolve("new-sub-dir");
        NotebookLMService svc = new NotebookLMService(subDir.toString());

        assertThat(subDir).doesNotExist();

        svc.export(TITLE, List.of());

        assertThat(subDir).exists().isDirectory();
    }

    // =========================================================================
    // Validation
    // =========================================================================

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
