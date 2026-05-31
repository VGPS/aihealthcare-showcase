package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.ArticleContentEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Unit tests for {@link NotebookLMService}.
 *
 * <p>Uses JUnit 5's {@link TempDir} to provide an isolated writable directory for
 * each test — no production filesystem paths are touched.  The service is
 * constructed directly (no Spring context) by passing temp-dir paths as the
 * {@code exportDirectory} and {@code summariesDirectory} constructor arguments,
 * exercising the same code path that the {@code @Value} injection follows at runtime.
 *
 * <p>The {@link ArticleContentEnricher} is spied so that {@code fetchPageHtml()}
 * never makes real HTTP calls.  Tests cover both outputs: the date-stamped summary
 * document (historical record) and individual per-article files (NotebookLM indexing),
 * including content enrichment, link-only filtering, title-based dedup, tag structure,
 * directory auto-creation, and blank-title validation.
 *
 * @author  Bill Blackmon
 * @version 5.0
 * @since   2026-04-13
 * @updated 2026-05-31
 */
class NotebookLMServiceTest {

    @TempDir
    Path tempDir;

    private Path exportDir;
    private Path summariesDir;
    private ArticleContentEnricher enricher;
    private NotebookLMService service;

    // --- shared fixtures ---

    private static final String TITLE = "AI in Healthcare Weekly";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    /** Minimum body length to pass the enricher's useful-body check. */
    private static final String USEFUL_BODY = "This is a sufficiently long body text that exceeds the minimum threshold for useful content.";

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

    private static NewsArticle articleWithMeta(String id, String topic, String articleTitle,
                                                String body, String author, Instant publishedAt) {
        return new NewsArticle(
                id,
                articleTitle,
                URI.create("https://example.com/" + id),
                body,
                topic,
                author, null, "TestSource", null,
                0.5,
                publishedAt
        );
    }

    @BeforeEach
    void setUp() {
        exportDir = tempDir.resolve("articles");
        summariesDir = tempDir.resolve("summaries");
        enricher = spy(new ArticleContentEnricher());
        service = new NotebookLMService(exportDir.toString(), summariesDir.toString(), "", enricher);
    }

    /** Returns today's expected summary filename. */
    private String todaySummaryFilename() {
        return LocalDate.now().format(DATE_FORMAT) + ".txt";
    }

    // =========================================================================
    // Summary document tests (historical record)
    // =========================================================================

    @Test
    void export_returnsSummaryFilePath() throws IOException {
        Path result = service.export(TITLE, List.of());

        assertThat(result).exists();
        assertThat(result.getParent()).isEqualTo(summariesDir);
    }

    @Test
    void export_summaryFilenameIsDateStamped() throws IOException {
        Path result = service.export(TITLE, List.of());

        assertThat(result.getFileName().toString()).isEqualTo(todaySummaryFilename());
    }

    @Test
    void export_summaryWrittenToSummariesDirectory() throws IOException {
        Path result = service.export(TITLE, List.of());

        assertThat(result).isEqualTo(summariesDir.resolve(todaySummaryFilename()));
    }

    @Test
    void export_summaryContentStartsWithTitleTag() throws IOException {
        Path result = service.export(TITLE, List.of());

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).startsWith("<title>" + TITLE + "</title>");
    }

    @Test
    void export_summaryContainsAllArticleTags() throws IOException {
        NewsArticle a = article("a-001", "AI Diagnostics", "AI Detects Cancer", USEFUL_BODY);
        Path result = service.export(TITLE, List.of(a));

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).contains("<source>TestSource</source>");
        assertThat(content).contains("<topic>AI Diagnostics</topic>");
        assertThat(content).contains("<title>AI Detects Cancer</title>");
        assertThat(content).contains("<url>https://example.com/a-001</url>");
        assertThat(content).contains("<body>" + USEFUL_BODY + "</body>");
    }

    @Test
    void export_summaryContainsMultipleArticles() throws IOException {
        List<NewsArticle> articles = List.of(
                article("a-001", "AI Diagnostics", "Article One", USEFUL_BODY + " one"),
                article("a-002", "ML Research",    "Article Two", USEFUL_BODY + " two")
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

    @Test
    void export_summaryStillContainsDuplicatedArticles() throws IOException {
        List<NewsArticle> articles = List.of(
                article("a-001", "AI", "Same Title", USEFUL_BODY + " first"),
                article("a-002", "ML", "Same Title", USEFUL_BODY + " second")
        );

        Path summary = service.export(TITLE, articles);

        String content = Files.readString(summary, StandardCharsets.UTF_8);
        assertThat(content).contains(USEFUL_BODY + " first");
        assertThat(content).contains(USEFUL_BODY + " second");
    }

    // =========================================================================
    // Individual article file tests (NotebookLM indexing)
    // =========================================================================

    @Test
    void export_createsIndividualFilePerArticle() throws IOException {
        List<NewsArticle> articles = List.of(
                article("a-001", "AI Diagnostics", "AI Detects Cancer", USEFUL_BODY + " one"),
                article("a-002", "ML Research", "ML in Clinical Trials", USEFUL_BODY + " two")
        );

        service.export(TITLE, articles);

        assertThat(exportDir.resolve("AI Detects Cancer.txt")).exists();
        assertThat(exportDir.resolve("ML in Clinical Trials.txt")).exists();
    }

    @Test
    void export_individualFileHasSourceTopicTitleUrlBodyTags() throws IOException {
        NewsArticle a = article("a-001", "AI Diagnostics", "AI Detects Cancer", USEFUL_BODY);
        service.export(TITLE, List.of(a));

        String content = Files.readString(exportDir.resolve("AI Detects Cancer.txt"), StandardCharsets.UTF_8);
        assertThat(content).contains("<source>TestSource</source>");
        assertThat(content).contains("<topic>AI Diagnostics</topic>");
        assertThat(content).contains("<title>AI Detects Cancer</title>");
        assertThat(content).contains("<url>https://example.com/a-001</url>");
        assertThat(content).contains("<body>" + USEFUL_BODY + "</body>");
    }

    @Test
    void export_individualFileDoesNotContainSummaryTitle() throws IOException {
        service.export(TITLE, List.of(
                article("a-001", "AI", "Test Article", USEFUL_BODY)
        ));

        String content = Files.readString(exportDir.resolve("Test Article.txt"), StandardCharsets.UTF_8);
        assertThat(content).doesNotContain(TITLE);
    }

    @Test
    void export_individualFilesWrittenToExportDirectory() throws IOException {
        service.export(TITLE, List.of(
                article("a-001", "AI", "Test Article", USEFUL_BODY)
        ));

        assertThat(exportDir.resolve("Test Article.txt")).exists();
        assertThat(summariesDir.resolve("Test Article.txt")).doesNotExist();
    }

    @Test
    void export_emptyArticleList_noIndividualFilesWritten() throws IOException {
        service.export(TITLE, List.of());

        if (Files.exists(exportDir)) {
            long txtFileCount = Files.list(exportDir)
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .count();
            assertThat(txtFileCount).isEqualTo(0);
        }
    }

    @Test
    void export_specialCharsInArticleTitleAreSanitized() throws IOException {
        service.export(TITLE, List.of(
                article("a-001", "AI", "AI/Healthcare: Weekly*Report?", USEFUL_BODY)
        ));

        assertThat(exportDir.resolve("AI_Healthcare_ Weekly_Report_.txt")).exists();
    }

    // =========================================================================
    // Content enrichment and link-only filtering
    // =========================================================================

    @Test
    void export_linkOnlyArticle_droppedFromExport() throws IOException {
        // Article with blank body and fetch fails — truly link-only
        NewsArticle linkOnly = article("a-001", "AI", "Link Only Article", "");
        doReturn("").when(enricher).fetchPageHtml("https://example.com/a-001");

        service.export(TITLE, List.of(linkOnly));

        // No individual file should be created
        if (Files.exists(exportDir)) {
            long txtFileCount = Files.list(exportDir)
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .count();
            assertThat(txtFileCount).isEqualTo(0);
        }
    }

    @Test
    void export_linkOnlyArticle_enrichedFromUrl_keptInExport() throws IOException {
        NewsArticle linkOnly = article("a-001", "AI", "Enriched Article", "");
        doReturn("<html><body><main>" + USEFUL_BODY + "</main></body></html>")
                .when(enricher).fetchPageHtml("https://example.com/a-001");

        service.export(TITLE, List.of(linkOnly));

        assertThat(exportDir.resolve("Enriched Article.txt")).exists();
        String content = Files.readString(exportDir.resolve("Enriched Article.txt"), StandardCharsets.UTF_8);
        assertThat(content).contains(USEFUL_BODY);
    }

    @Test
    void export_mixedArticles_onlyUsefulOnesExported() throws IOException {
        NewsArticle withBody = article("a-001", "AI", "Good Article", USEFUL_BODY);
        NewsArticle linkOnly = article("a-002", "ML", "Link Article", "");
        doReturn("").when(enricher).fetchPageHtml("https://example.com/a-002");

        service.export(TITLE, List.of(withBody, linkOnly));

        assertThat(exportDir.resolve("Good Article.txt")).exists();
        // Link-only (blank body + failed fetch) should be filtered out
        long txtFileCount = Files.list(exportDir)
                .filter(p -> p.getFileName().toString().endsWith(".txt"))
                .count();
        assertThat(txtFileCount).isEqualTo(1);
    }

    // =========================================================================
    // Individual file — title-based dedup
    // =========================================================================

    @Test
    void export_duplicateTitlesInBatch_onlyFirstIndividualFileIsWritten() throws IOException {
        List<NewsArticle> articles = List.of(
                article("a-001", "AI", "Same Title", USEFUL_BODY + " first"),
                article("a-002", "ML", "Same Title", USEFUL_BODY + " second")
        );

        service.export(TITLE, articles);

        String content = Files.readString(exportDir.resolve("Same Title.txt"), StandardCharsets.UTF_8);
        assertThat(content).contains(USEFUL_BODY + " first");
    }

    @Test
    void export_duplicateTitlesCaseInsensitive_onlyFirstIndividualFileIsWritten() throws IOException {
        List<NewsArticle> articles = List.of(
                article("a-001", "AI", "AI Cancer Detection", USEFUL_BODY + " first"),
                article("a-002", "ML", "ai cancer detection", USEFUL_BODY + " second")
        );

        service.export(TITLE, articles);

        long txtFileCount = Files.list(exportDir)
                .filter(p -> p.getFileName().toString().endsWith(".txt"))
                .count();
        assertThat(txtFileCount).isEqualTo(1);
    }

    @Test
    void export_individualFileAlreadyOnDisk_isSkipped() throws IOException {
        Files.createDirectories(exportDir);
        Files.writeString(exportDir.resolve("Existing Article.txt"), "old content", StandardCharsets.UTF_8);

        service.export(TITLE, List.of(
                article("a-001", "AI", "Existing Article", USEFUL_BODY)
        ));

        String content = Files.readString(exportDir.resolve("Existing Article.txt"), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("old content");
    }

    // =========================================================================
    // Body-text fallback in summary (enriched articles use real body)
    // =========================================================================

    @Test
    void export_articleWithUsefulBody_summaryContainsBody() throws IOException {
        NewsArticle a = article("a-001", "AI", "Full Article", USEFUL_BODY);
        Path result = service.export(TITLE, List.of(a));

        String content = Files.readString(result, StandardCharsets.UTF_8);
        assertThat(content).contains("<body>" + USEFUL_BODY + "</body>");
    }

    // =========================================================================
    // Directory auto-creation
    // =========================================================================

    @Test
    void export_directoriesDoNotExist_createdAutomatically() throws IOException {
        Path newExportDir = tempDir.resolve("new-export");
        Path newSummariesDir = tempDir.resolve("new-summaries");
        NotebookLMService svc = new NotebookLMService(
                newExportDir.toString(), newSummariesDir.toString(), "", enricher);

        assertThat(newExportDir).doesNotExist();
        assertThat(newSummariesDir).doesNotExist();

        svc.export(TITLE, List.of(article("a-001", "AI", "Test", USEFUL_BODY)));

        assertThat(newExportDir).exists().isDirectory();
        assertThat(newSummariesDir).exists().isDirectory();
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

    // =========================================================================
    // HTML summary document tests
    // =========================================================================

    @Test
    void export_htmlFileCreatedAlongsideTxtFile() throws IOException {
        service.export(TITLE, List.of());

        String expectedHtml = LocalDate.now().format(DATE_FORMAT) + ".html";
        assertThat(summariesDir.resolve(expectedHtml)).exists();
    }

    @Test
    void export_htmlFileIsValidHtmlDocument() throws IOException {
        service.export(TITLE, List.of());

        String expectedHtml = LocalDate.now().format(DATE_FORMAT) + ".html";
        String content = Files.readString(summariesDir.resolve(expectedHtml), StandardCharsets.UTF_8);
        assertThat(content).startsWith("<!DOCTYPE html>");
        assertThat(content).contains("<html lang=\"en\">");
        assertThat(content).contains("</html>");
    }

    @Test
    void export_htmlFileContainsTitleInHeadAndBody() throws IOException {
        service.export(TITLE, List.of());

        String expectedHtml = LocalDate.now().format(DATE_FORMAT) + ".html";
        String content = Files.readString(summariesDir.resolve(expectedHtml), StandardCharsets.UTF_8);
        assertThat(content).contains("<title>" + TITLE + "</title>");
        assertThat(content).contains("<h1>" + TITLE + "</h1>");
    }

    @Test
    void export_htmlFileGroupsArticlesBySource() throws IOException {
        NewsArticle a = article("a-001", "AI Diagnostics", "AI Detects Cancer", USEFUL_BODY);

        service.export(TITLE, List.of(a));

        String expectedHtml = LocalDate.now().format(DATE_FORMAT) + ".html";
        String content = Files.readString(summariesDir.resolve(expectedHtml), StandardCharsets.UTF_8);
        assertThat(content).contains("TestSource");
        assertThat(content).contains("AI Detects Cancer");
    }

    @Test
    void export_htmlFileTitleEscapesHtmlEntities() throws IOException {
        service.export("AI & Healthcare <Weekly>", List.of());

        String expectedHtml = LocalDate.now().format(DATE_FORMAT) + ".html";
        String content = Files.readString(summariesDir.resolve(expectedHtml), StandardCharsets.UTF_8);
        assertThat(content).contains("AI &amp; Healthcare &lt;Weekly&gt;");
        assertThat(content).doesNotContain("<Weekly>");
    }

    // =========================================================================
    // HTML article meta (author, date, source)
    // =========================================================================

    @Test
    void export_htmlArticleShowsAuthorAndDate() throws IOException {
        // 2026-05-15T12:00:00Z
        Instant published = Instant.parse("2026-05-15T12:00:00Z");
        NewsArticle a = articleWithMeta("a-001", "AI", "Safe AI Platform", USEFUL_BODY,
                "Jane Doe", published);

        service.export(TITLE, List.of(a));

        String expectedHtml = LocalDate.now().format(DATE_FORMAT) + ".html";
        String content = Files.readString(summariesDir.resolve(expectedHtml), StandardCharsets.UTF_8);
        assertThat(content).contains("article-meta");
        assertThat(content).contains("Jane Doe");
        assertThat(content).contains("May 15, 2026");
        assertThat(content).contains("TestSource");
    }

    @Test
    void export_htmlArticleOmitsAuthorWhenNull() throws IOException {
        Instant published = Instant.parse("2026-03-10T08:00:00Z");
        NewsArticle a = articleWithMeta("a-001", "AI", "AI in Oncology", USEFUL_BODY,
                null, published);

        service.export(TITLE, List.of(a));

        String expectedHtml = LocalDate.now().format(DATE_FORMAT) + ".html";
        String content = Files.readString(summariesDir.resolve(expectedHtml), StandardCharsets.UTF_8);
        assertThat(content).contains("Mar 10, 2026");
        assertThat(content).contains("TestSource");
        // No dangling comma when author is absent
        assertThat(content).doesNotContain("- ,");
    }

    @Test
    void export_htmlArticleOmitsDateWhenNull() throws IOException {
        NewsArticle a = articleWithMeta("a-001", "AI", "AI Detects Cancer", USEFUL_BODY,
                "John Smith", null);

        service.export(TITLE, List.of(a));

        String expectedHtml = LocalDate.now().format(DATE_FORMAT) + ".html";
        String content = Files.readString(summariesDir.resolve(expectedHtml), StandardCharsets.UTF_8);
        assertThat(content).contains("John Smith");
        assertThat(content).contains("TestSource");
        // No comma before source when date is absent
        assertThat(content).doesNotContain("Smith,");
    }

    @Test
    void export_htmlArticleNoBodyDivOrReadMore() throws IOException {
        NewsArticle a = article("a-001", "AI", "Test Article", USEFUL_BODY);

        service.export(TITLE, List.of(a));

        String expectedHtml = LocalDate.now().format(DATE_FORMAT) + ".html";
        String content = Files.readString(summariesDir.resolve(expectedHtml), StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("article-body");
        assertThat(content).doesNotContain("read-more");
        assertThat(content).doesNotContain("Read article");
    }
}
