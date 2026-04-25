package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.ArticleContentEnricher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Exports article search results in two complementary formats:
 *
 * <ol>
 *   <li><b>Summary document</b> — a single date-stamped file written to the
 *       summaries directory ({@code aihealthcare.notebooklm.summaries-directory}).
 *       The filename follows the {@code yyyy_MM_dd.txt} pattern so that each
 *       day's export is preserved as a historical record.</li>
 *   <li><b>Individual article files</b> — one file per article, named after
 *       the sanitized article title, written to the main export directory
 *       ({@code aihealthcare.notebooklm.directory}).  These files are consumed
 *       directly by NotebookLM for indexing.  Duplicate titles (case-insensitive)
 *       within a batch and files that already exist on disk are skipped.</li>
 * </ol>
 *
 * <p>This service is the final step in the NotebookLM pipeline:
 * <ol>
 *   <li>Search results arrive as a {@link List} of {@link NewsArticle} objects.</li>
 *   <li>Articles are passed through {@link #filter} which enriches articles
 *       that have no body text by fetching their URL content via
 *       {@link ArticleContentEnricher}, then drops any articles that still
 *       have no meaningful content after enrichment.</li>
 *   <li>Filtered articles are serialized to structured plain-text using
 *       XML-style demarcation tags ({@code <topic>}, {@code <title>}, {@code <body>})
 *       that NotebookLM can parse and index per section.</li>
 *   <li>The summary document is written to the summaries directory; individual
 *       article files are written to the main export directory.  Both directories
 *       are created automatically if they do not yet exist.</li>
 * </ol>
 *
 * <p>This class lives in {@code infrastructure.delivery} because writing to the
 * filesystem is an outbound infrastructure concern.  It carries no business logic —
 * the caller decides which articles to pass in and what title to use.
 *
 * <p>Directory paths are read from {@code application.yml}:
 * <pre>{@code
 * aihealthcare:
 *   notebooklm:
 *     directory: NotebookLMDirectory
 *     summaries-directory: NotebookLMDirectory/summaries
 * }</pre>
 *
 * @author  Bill Blackmon
 * @version 5.0
 * @since   2026-04-13
 * @updated 2026-04-25
 */
@Slf4j
@Service
public class NotebookLMService {

    private static final DateTimeFormatter SUMMARY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy_MM_dd");

    private final String exportDirectory;
    private final String summariesDirectory;
    private final ArticleContentEnricher contentEnricher;

    /**
     * Constructs the service with the configured directories and content enricher.
     *
     * @param exportDirectory    Path to the directory where individual article files
     *                           are written.  Resolved from
     *                           {@code aihealthcare.notebooklm.directory};
     *                           defaults to {@code NotebookLMDirectory} if unset.
     * @param summariesDirectory Path to the directory where date-stamped summary
     *                           files are written.  Resolved from
     *                           {@code aihealthcare.notebooklm.summaries-directory};
     *                           defaults to {@code NotebookLMDirectory/summaries}.
     * @param contentEnricher    Enricher that fetches page content for articles
     *                           with empty body text.
     */
    public NotebookLMService(
            @Value("${aihealthcare.notebooklm.directory:NotebookLMDirectory}") String exportDirectory,
            @Value("${aihealthcare.notebooklm.summaries-directory:NotebookLMDirectory/summaries}") String summariesDirectory,
            ArticleContentEnricher contentEnricher) {
        log.debug("NotebookLMService() | exportDirectory={}, summariesDirectory={}, contentEnricher={}",
                  exportDirectory, summariesDirectory, contentEnricher.getClass().getSimpleName());
        this.exportDirectory = exportDirectory;
        this.summariesDirectory = summariesDirectory;
        this.contentEnricher = contentEnricher;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Filters, formats, and writes the supplied articles to both a date-stamped
     * summary document and individual per-article files.
     *
     * <p>The summary document is named {@code yyyy_MM_dd.txt} (using the current
     * date) and written to the summaries directory — used as a historical record.
     *
     * <p>Individual article files are named {@code <sanitized-article-title>.txt}
     * and written to the main export directory.  Duplicate titles are detected by
     * normalizing the sanitized filename to lowercase — the second (and subsequent)
     * articles with the same title are skipped and logged.  Files that already
     * exist on disk from a previous run are also skipped.
     *
     * @param title    Display title for the export (used in the summary document
     *                 header).  Must not be blank.
     * @param articles Articles to export; may be empty, in which case a summary file
     *                 containing only the title tag is written.
     * @return The {@link Path} of the summary document.
     * @throws IOException              if a directory cannot be created or a file
     *                                  cannot be written.
     * @throws IllegalArgumentException if {@code title} is blank.
     */
    public Path export(String title, List<NewsArticle> articles) throws IOException {
        log.debug("export() | title={}, articleCount={}", title, articles == null ? 0 : articles.size());

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (articles == null) {
            articles = List.of();
        }

        List<NewsArticle> filtered = filter(articles);
        log.info("export() | Articles after filtering: {} of {} kept", filtered.size(), articles.size());

        // 1. Write the date-stamped summary document (historical record)
        String summaryContent = formatForNotebookLm(title, filtered);
        Path summaryPath = writeSummaryFile(summaryContent);
        log.info("export() | Summary document written: {}", summaryPath.toAbsolutePath());

        // 2. Write individual per-article files (for NotebookLM indexing)
        writeIndividualFiles(filtered);

        log.info("export() | Wrote {} articles; summary at {}", filtered.size(), summaryPath.toAbsolutePath());
        log.debug("export() | return={}", summaryPath);
        return summaryPath;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Enriches and filters the article list before export.
     *
     * <p>First, articles with empty or insufficient body text are enriched by
     * fetching their URL content via {@link ArticleContentEnricher}.  Then,
     * any articles that still have no meaningful body text (less than
     * {@link ArticleContentEnricher#MIN_USEFUL_LENGTH} characters) are dropped.
     * This ensures only articles with useful content reach the export stage.
     *
     * @param articles The raw search-result articles.
     * @return A filtered list containing only articles with meaningful content.
     */
    private List<NewsArticle> filter(List<NewsArticle> articles) {
        log.debug("filter() | articleCount={}", articles.size());

        // Step 1: Enrich articles with empty body text by fetching URL content
        List<NewsArticle> enriched = contentEnricher.enrich(articles);

        // Step 2: Drop articles that still have no meaningful body text
        List<NewsArticle> result = new ArrayList<>();
        int dropped = 0;
        for (NewsArticle article : enriched) {
            if (contentEnricher.hasUsefulBody(article)) {
                result.add(article);
            } else {
                log.debug("filter() | dropping link-only article: '{}' ({})",
                          article.title(), article.url());
                dropped++;
            }
        }

        log.info("filter() | {} articles kept, {} link-only articles dropped",
                 result.size(), dropped);
        log.debug("filter() | return={} articles", result.size());
        return result;
    }

    /**
     * Serializes all articles to a single NotebookLM-compatible structured text
     * document for historical record keeping.
     *
     * <p>The output format is:
     * <pre>
     * &lt;title&gt;Export Title&lt;/title&gt;
     *
     * &lt;source&gt;Source Name&lt;/source&gt;
     * &lt;topic&gt;Topic Name&lt;/topic&gt;
     * &lt;title&gt;Article Title&lt;/title&gt;
     * &lt;url&gt;https://...&lt;/url&gt;
     * &lt;body&gt;Article body text or URL fallback.&lt;/body&gt;
     *
     * ...repeated per article...
     * </pre>
     *
     * @param title    The export title written as the first tag in the document.
     * @param articles The filtered articles to serialize.
     * @return The formatted document as a single {@link String}.
     */
    private String formatForNotebookLm(String title, List<NewsArticle> articles) {
        log.debug("formatForNotebookLm() | title={}, articleCount={}", title, articles.size());

        StringBuilder sb = new StringBuilder();
        sb.append("<title>").append(title).append("</title>\n\n");

        for (NewsArticle article : articles) {
            String source = article.sourceName() != null ? article.sourceName() : "";
            String topic = article.topic() != null ? article.topic() : "";
            String articleTitle = article.title() != null ? article.title() : "";
            String url = article.url() != null ? article.url().toString() : "";
            String body = resolveBody(article);

            sb.append("<source>").append(source).append("</source>\n");
            sb.append("<topic>").append(topic).append("</topic>\n");
            sb.append("<title>").append(articleTitle).append("</title>\n");
            sb.append("<url>").append(url).append("</url>\n");
            sb.append("<body>").append(body).append("</body>\n\n");
        }

        String result = sb.toString();
        log.debug("formatForNotebookLm() | return={} chars", result.length());
        return result;
    }

    /**
     * Serializes a single article to NotebookLM-compatible structured text
     * for individual file export.
     *
     * <p>The output format is:
     * <pre>
     * &lt;source&gt;Source Name&lt;/source&gt;
     * &lt;topic&gt;Topic Name&lt;/topic&gt;
     * &lt;title&gt;Article Title&lt;/title&gt;
     * &lt;url&gt;https://...&lt;/url&gt;
     * &lt;body&gt;Article body text or URL fallback.&lt;/body&gt;
     * </pre>
     *
     * @param article The article to serialize.
     * @return The formatted document as a single {@link String}.
     */
    private String formatSingleArticle(NewsArticle article) {
        log.debug("formatSingleArticle() | articleId={}", article.articleId());

        String topic = article.topic() != null ? article.topic() : "";
        String articleTitle = article.title() != null ? article.title() : "";
        String source = article.sourceName() != null ? article.sourceName() : "";
        String url = article.url() != null ? article.url().toString() : "";
        String body = resolveBody(article);

        StringBuilder sb = new StringBuilder();
        sb.append("<source>").append(source).append("</source>\n");
        sb.append("<topic>").append(topic).append("</topic>\n");
        sb.append("<title>").append(articleTitle).append("</title>\n");
        sb.append("<url>").append(url).append("</url>\n");
        sb.append("<body>").append(body).append("</body>\n");

        String result = sb.toString();
        log.debug("formatSingleArticle() | return={} chars", result.length());
        return result;
    }

    /**
     * Writes individual per-article files into the export directory.
     *
     * <p>Each article is written as {@code <sanitized-article-title>.txt}.
     * Duplicate titles (case-insensitive) within the batch are skipped.
     * Files that already exist on disk from a previous run are also skipped.
     *
     * @param articles The filtered articles to write individually.
     * @throws IOException if a file cannot be written.
     */
    private void writeIndividualFiles(List<NewsArticle> articles) throws IOException {
        log.debug("writeIndividualFiles() | articleCount={}", articles.size());

        Path dir = ensureDirectory(Paths.get(exportDirectory));
        Set<String> seenTitles = new HashSet<>();
        int written = 0;
        int skippedDuplicate = 0;
        int skippedExists = 0;

        for (NewsArticle article : articles) {
            String articleTitle = article.title() != null && !article.title().isBlank()
                    ? article.title()
                    : article.articleId();
            String sanitized = sanitizeFilename(articleTitle);
            String normalizedKey = sanitized.toLowerCase();

            if (seenTitles.contains(normalizedKey)) {
                log.debug("writeIndividualFiles() | skipping duplicate title in batch: '{}'", articleTitle);
                skippedDuplicate++;
                continue;
            }
            seenTitles.add(normalizedKey);

            Path file = dir.resolve(sanitized + ".txt");
            if (Files.exists(file)) {
                log.debug("writeIndividualFiles() | skipping already-on-disk: {}", file.getFileName());
                skippedExists++;
                continue;
            }

            String content = formatSingleArticle(article);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            written++;
            log.debug("writeIndividualFiles() | wrote {}", file.getFileName());
        }

        log.info("writeIndividualFiles() | {} files written, {} duplicate titles skipped, {} already on disk",
                 written, skippedDuplicate, skippedExists);
        log.debug("writeIndividualFiles() | return=void");
    }

    /**
     * Resolves the body text for a single article.
     *
     * <p>Falls back to the article's canonical URL string when {@code bodyText}
     * is absent or blank, ensuring the {@code <body>} tag is never empty.
     *
     * @param article The article to resolve body text for.
     * @return Non-null body string (may be an empty string if both bodyText and url are absent).
     */
    private String resolveBody(NewsArticle article) {
        log.debug("resolveBody() | articleId={}", article.articleId());

        String result;
        if (article.bodyText() != null && !article.bodyText().isBlank()) {
            result = article.bodyText();
        } else if (article.url() != null) {
            result = article.url().toString();
        } else {
            result = "";
        }

        log.debug("resolveBody() | return={} chars", result.length());
        return result;
    }

    /**
     * Writes the summary content to a date-stamped file in the summaries directory.
     *
     * <p>The filename is {@code yyyy_MM_dd.txt} based on the current date.
     * The summaries directory is created if it does not exist.
     *
     * @param content The formatted summary document content.
     * @return The {@link Path} of the written summary file.
     * @throws IOException if the directory cannot be created or the file cannot be written.
     */
    private Path writeSummaryFile(String content) throws IOException {
        log.debug("writeSummaryFile() | contentLength={}", content.length());

        Path dir = ensureDirectory(Paths.get(summariesDirectory));
        String filename = LocalDate.now().format(SUMMARY_DATE_FORMAT) + ".txt";
        Path file = dir.resolve(filename);

        Files.writeString(file, content, StandardCharsets.UTF_8);

        log.debug("writeSummaryFile() | return={}", file.toAbsolutePath());
        return file;
    }

    /**
     * Ensures the given directory exists, creating it (and parents) if absent.
     *
     * @param dir The directory path to ensure.
     * @return The same directory path, guaranteed to exist.
     * @throws IOException if the directory cannot be created.
     */
    private Path ensureDirectory(Path dir) throws IOException {
        log.debug("ensureDirectory() | dir={}", dir);

        if (!Files.exists(dir)) {
            log.info("ensureDirectory() | Directory does not exist — creating: {}",
                     dir.toAbsolutePath());
            Files.createDirectories(dir);
        }

        log.debug("ensureDirectory() | return={}", dir.toAbsolutePath());
        return dir;
    }

    /**
     * Converts a title string into a safe filesystem filename by replacing
     * characters that are invalid on Windows, macOS, and Linux with underscores.
     *
     * <p>Replaced characters: {@code \ / : * ? " < > |}
     *
     * @param title The raw title string.
     * @return A filename-safe version of the title with leading/trailing whitespace removed.
     */
    private String sanitizeFilename(String title) {
        log.debug("sanitizeFilename() | title={}", title);
        String result = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        log.debug("sanitizeFilename() | return={}", result);
        return result;
    }
}
