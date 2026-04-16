package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports article search results to a NotebookLM-compatible text file.
 *
 * <p>This service is the final step in the NotebookLM pipeline:
 * <ol>
 *   <li>Search results arrive as a {@link List} of {@link NewsArticle} objects.</li>
 *   <li>Articles are passed through {@link #filter} — currently a pass-through;
 *       AI-based relevance scoring will be introduced in a future slice.</li>
 *   <li>Filtered articles are serialized to a structured plain-text format using
 *       XML-style demarcation tags ({@code <topic>}, {@code <title>}, {@code <body>})
 *       that NotebookLM can parse and index per section.</li>
 *   <li>The resulting text is written to a file named after the export title inside
 *       the directory configured by {@code aihealthcare.notebooklm.directory}
 *       (default: {@code NotebookLMDirectory} relative to the working directory).
 *       The directory is created automatically if it does not yet exist.</li>
 * </ol>
 *
 * <p>This class lives in {@code infrastructure.delivery} because writing to the
 * filesystem is an outbound infrastructure concern.  It carries no business logic —
 * the caller decides which articles to pass in and what title to use.
 *
 * <p>The export directory path is read from {@code application.yml}:
 * <pre>{@code
 * aihealthcare:
 *   notebooklm:
 *     directory: NotebookLMDirectory
 * }</pre>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
@Slf4j
@Service
public class NotebookLMService {

    private final String exportDirectory;

    /**
     * Constructs the service with the configured export directory.
     *
     * @param exportDirectory Path to the directory where export files are written.
     *                        Resolved from {@code aihealthcare.notebooklm.directory};
     *                        defaults to {@code NotebookLMDirectory} if unset.
     */
    public NotebookLMService(
            @Value("${aihealthcare.notebooklm.directory:NotebookLMDirectory}") String exportDirectory) {
        log.debug("NotebookLMService() | exportDirectory={}", exportDirectory);
        this.exportDirectory = exportDirectory;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Filters, formats, and writes the supplied articles to a NotebookLM text file.
     *
     * <p>The output file is named {@code <sanitized-title>.txt} and is placed
     * inside the configured export directory.  The directory is created if absent.
     *
     * @param title    Display title for the export (also used as the filename).
     *                 Must not be blank.
     * @param articles Articles to export; may be empty, in which case a file
     *                 containing only the title tag is written.
     * @return The {@link Path} of the written file.
     * @throws IOException              if the directory cannot be created or the file
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

        String content = formatForNotebookLm(title, filtered);
        Path outputPath = writeToFile(title, content);

        log.info("export() | Wrote {} articles to {}", filtered.size(), outputPath.toAbsolutePath());
        log.debug("export() | return={}", outputPath);
        return outputPath;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Filters the article list before export.
     *
     * <p><b>Current behaviour:</b> all articles are passed through unchanged.
     *
     * <p><b>TODO (future slice):</b> Replace with AI-based relevance filtering
     * via the Agent step.  Each article will be scored against a configurable
     * relevance threshold; articles that fall below the threshold are excluded
     * so that only high-signal content reaches the NotebookLM index.
     *
     * @param articles The raw search-result articles.
     * @return A filtered (currently identical) copy of the input list.
     */
    private List<NewsArticle> filter(List<NewsArticle> articles) {
        log.debug("filter() | articleCount={}", articles.size());

        // TODO (future slice): AI-based relevance scoring and threshold filtering.
        //   Planned approach: call ArticleSearchPort with a relevance query, score
        //   each article against the newsletter topic context, and drop articles
        //   whose score falls below aihealthcare.notebooklm.relevance-threshold.
        List<NewsArticle> result = new ArrayList<>();
        for (NewsArticle article : articles) {
            result.add(article);
        }

        log.debug("filter() | return={} articles", result.size());
        return result;
    }

    /**
     * Serializes articles to NotebookLM-compatible structured text.
     *
     * <p>The output format is:
     * <pre>
     * &lt;title&gt;Export Title&lt;/title&gt;
     *
     * &lt;topic&gt;Topic Name&lt;/topic&gt;
     * &lt;title&gt;Article Title&lt;/title&gt;
     * &lt;body&gt;Article body text or URL fallback.&lt;/body&gt;
     *
     * ...repeated per article...
     * </pre>
     *
     * <p>If an article has no body text, the canonical URL is substituted so that
     * NotebookLM always has a non-empty {@code <body>} to index.
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
            String topic = article.topic() != null ? article.topic() : "";
            String articleTitle = article.title() != null ? article.title() : "";
            String body = resolveBody(article);

            sb.append("<topic>").append(topic).append("</topic>\n");
            sb.append("<title>").append(articleTitle).append("</title>\n");
            sb.append("<body>").append(body).append("</body>\n\n");
        }

        String result = sb.toString();
        log.debug("formatForNotebookLm() | return={} chars", result.length());
        return result;
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
     * Ensures the export directory exists and writes the formatted content to a
     * file named {@code <sanitized-title>.txt}.
     *
     * @param title   The export title (used to derive the filename).
     * @param content The formatted document content to write.
     * @return The {@link Path} of the written file.
     * @throws IOException if the directory cannot be created or the file cannot be written.
     */
    private Path writeToFile(String title, String content) throws IOException {
        log.debug("writeToFile() | title={}, contentLength={}", title, content.length());

        Path dir = Paths.get(exportDirectory);
        if (!Files.exists(dir)) {
            log.info("writeToFile() | Export directory does not exist — creating: {}",
                     dir.toAbsolutePath());
            Files.createDirectories(dir);
        }

        String filename = sanitizeFilename(title) + ".txt";
        Path file = dir.resolve(filename);

        Files.writeString(file, content, StandardCharsets.UTF_8);

        log.debug("writeToFile() | return={}", file.toAbsolutePath());
        return file;
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
