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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exports article search results in three complementary formats:
 *
 * <ol>
 *   <li><b>Summary document (.txt)</b> — a single date-stamped plain-text file written
 *       to the summaries directory ({@code aihealthcare.notebooklm.summaries-directory}).
 *       The filename follows the {@code yyyy_MM_dd.txt} pattern so that each day's
 *       export is preserved as a historical record.</li>
 *   <li><b>Summary document (.html)</b> — a companion HTML file with the same base name
 *       as the plain-text summary, written to the same summaries directory.  Articles are
 *       grouped by source and rendered as styled cards suitable for browser viewing.</li>
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
 *   <li>Both summary files and all individual article files are written; all
 *       directories are created automatically if they do not yet exist.</li>
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
 * @version 6.0
 * @since   2026-04-13
 * @updated 2026-04-28
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

        // 1. Write the date-stamped plain-text summary (historical record)
        String summaryContent = formatForNotebookLm(title, filtered);
        Path summaryPath = writeSummaryFile(summaryContent);
        log.info("export() | Summary .txt written: {}", summaryPath.toAbsolutePath());

        // 2. Write the companion HTML summary (browser-readable historical record)
        String htmlContent = formatForHtml(title, filtered);
        Path htmlPath = writeSummaryHtmlFile(htmlContent);
        log.info("export() | Summary .html written: {}", htmlPath.toAbsolutePath());

        // 3. Write individual per-article files (for NotebookLM indexing)
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
     * Generates a fully styled HTML document from the filtered article list.
     *
     * <p>Articles are grouped by source name and rendered as styled cards.
     * Each card shows the article title as a clickable link, the body text,
     * and a source badge.  The document is self-contained (inline CSS only —
     * no external dependencies) so it can be opened directly in a browser.
     *
     * <p>Plain-text fields (title, source) are HTML-escaped.  Body text is
     * written as-is because RSS feeds already deliver it as HTML-encoded text
     * or HTML markup (e.g., Google News {@code <a>} elements), which browsers
     * render correctly without further escaping.
     *
     * @param title    Display title used in the {@code <title>} tag and as the
     *                 page heading.
     * @param articles The filtered articles to render.
     * @return A complete HTML document as a {@link String}.
     */
    private String formatForHtml(String title, List<NewsArticle> articles) {
        log.debug("formatForHtml() | title={}, articleCount={}", title, articles.size());

        // Group articles by source, preserving insertion order
        Map<String, List<NewsArticle>> bySource = new LinkedHashMap<>();
        for (NewsArticle article : articles) {
            String source = article.sourceName() != null && !article.sourceName().isBlank()
                    ? article.sourceName() : "Unknown Source";
            if (!bySource.containsKey(source)) {
                bySource.put(source, new ArrayList<>());
            }
            bySource.get(source).add(article);
        }

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"en\">\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <title>").append(escapeHtml(title)).append("</title>\n");
        sb.append("  <style>\n");
        sb.append("    * { box-sizing: border-box; margin: 0; padding: 0; }\n");
        sb.append("    body { font-family: Georgia, 'Times New Roman', serif; background: #f4f6f9;\n");
        sb.append("           color: #1a1a2e; padding: 32px 16px; }\n");
        sb.append("    .page { max-width: 960px; margin: 0 auto; }\n");
        sb.append("    h1 { font-size: 1.9em; color: #1a3a5c; border-bottom: 3px solid #2c5f8a;\n");
        sb.append("         padding-bottom: 12px; margin-bottom: 6px; }\n");
        sb.append("    .meta { color: #666; font-size: 0.88em; margin-bottom: 36px;\n");
        sb.append("            font-family: Arial, sans-serif; }\n");
        sb.append("    .source-block { margin-bottom: 36px; border-radius: 8px;\n");
        sb.append("                    box-shadow: 0 2px 6px rgba(0,0,0,0.08); overflow: hidden; }\n");
        sb.append("    .source-header { background: #2c5f8a; color: #fff; padding: 10px 18px;\n");
        sb.append("                     font-family: Arial, sans-serif; font-size: 1em;\n");
        sb.append("                     font-weight: bold; letter-spacing: 0.03em; }\n");
        sb.append("    .article { background: #fff; padding: 18px 22px;\n");
        sb.append("               border-top: 1px solid #e4eaf1; }\n");
        sb.append("    .article:first-of-type { border-top: none; }\n");
        sb.append("    .article-title { font-size: 1.05em; font-weight: bold; margin-bottom: 6px; }\n");
        sb.append("    .article-title a { color: #1a3a5c; text-decoration: none; }\n");
        sb.append("    .article-title a:hover { color: #2c5f8a; text-decoration: underline; }\n");
        sb.append("    .article-body { font-size: 0.93em; color: #444; line-height: 1.65;\n");
        sb.append("                    margin-top: 6px; }\n");
        sb.append("    .toc { background: #fff; border: 1px solid #dce4ec; border-radius: 8px;\n");
        sb.append("           padding: 20px 24px; margin-bottom: 36px; }\n");
        sb.append("    .toc h2 { font-size: 1em; color: #2c5f8a; margin-bottom: 10px;\n");
        sb.append("              font-family: Arial, sans-serif; }\n");
        sb.append("    .toc ul { list-style: none; padding: 0; }\n");
        sb.append("    .toc li { padding: 3px 0; font-family: Arial, sans-serif;\n");
        sb.append("              font-size: 0.9em; }\n");
        sb.append("    .toc li a { color: #2c5f8a; text-decoration: none; }\n");
        sb.append("    .toc li a:hover { text-decoration: underline; }\n");
        sb.append("    .badge { display: inline-block; background: #e8f0fa; color: #2c5f8a;\n");
        sb.append("             font-size: 0.75em; padding: 1px 7px; border-radius: 10px;\n");
        sb.append("             font-family: Arial, sans-serif; margin-left: 6px;\n");
        sb.append("             vertical-align: middle; }\n");
        sb.append("  </style>\n</head>\n<body>\n<div class=\"page\">\n");

        // Page heading
        sb.append("  <h1>").append(escapeHtml(title)).append("</h1>\n");
        sb.append("  <div class=\"meta\">Generated ").append(date)
          .append(" &bull; ").append(articles.size()).append(" articles across ")
          .append(bySource.size()).append(" sources</div>\n\n");

        // Table of contents
        sb.append("  <div class=\"toc\">\n    <h2>Sources</h2>\n    <ul>\n");
        for (Map.Entry<String, List<NewsArticle>> entry : bySource.entrySet()) {
            String anchorId = "src-" + sanitizeFilename(entry.getKey()).replace(" ", "-");
            sb.append("      <li><a href=\"#").append(escapeHtml(anchorId)).append("\">")
              .append(escapeHtml(entry.getKey()))
              .append("</a> <span class=\"badge\">").append(entry.getValue().size()).append("</span></li>\n");
        }
        sb.append("    </ul>\n  </div>\n\n");

        // Article sections grouped by source
        for (Map.Entry<String, List<NewsArticle>> entry : bySource.entrySet()) {
            String anchorId = "src-" + sanitizeFilename(entry.getKey()).replace(" ", "-");
            sb.append("  <div class=\"source-block\" id=\"").append(escapeHtml(anchorId)).append("\">\n");
            sb.append("    <div class=\"source-header\">").append(escapeHtml(entry.getKey())).append("</div>\n");

            for (NewsArticle article : entry.getValue()) {
                String articleTitle = article.title() != null ? article.title() : "(no title)";
                String url = article.url() != null ? article.url().toString() : "#";
                String body = resolveBody(article);

                sb.append("    <div class=\"article\">\n");
                sb.append("      <div class=\"article-title\"><a href=\"").append(escapeHtml(url))
                  .append("\" target=\"_blank\" rel=\"noopener\">")
                  .append(escapeHtml(articleTitle)).append("</a></div>\n");
                sb.append("      <div class=\"article-body\">").append(body).append("</div>\n");
                sb.append("    </div>\n");
            }

            sb.append("  </div>\n\n");
        }

        sb.append("</div>\n</body>\n</html>\n");

        String result = sb.toString();
        log.debug("formatForHtml() | return={} chars", result.length());
        return result;
    }

    /**
     * Writes the HTML summary content to a date-stamped {@code .html} file in
     * the summaries directory.
     *
     * <p>The filename is {@code yyyy_MM_dd.html} based on the current date,
     * matching the companion plain-text file.  The summaries directory is
     * created if it does not exist.
     *
     * @param content The formatted HTML document content.
     * @return The {@link Path} of the written HTML file.
     * @throws IOException if the directory cannot be created or the file cannot be written.
     */
    private Path writeSummaryHtmlFile(String content) throws IOException {
        log.debug("writeSummaryHtmlFile() | contentLength={}", content.length());

        Path dir = ensureDirectory(Paths.get(summariesDirectory));
        String filename = LocalDate.now().format(SUMMARY_DATE_FORMAT) + ".html";
        Path file = dir.resolve(filename);

        Files.writeString(file, content, StandardCharsets.UTF_8);

        log.debug("writeSummaryHtmlFile() | return={}", file.toAbsolutePath());
        return file;
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
     * Escapes the five reserved HTML characters in plain-text values that are
     * written into HTML element content or attribute values.
     *
     * <p>Body text is intentionally <em>not</em> passed through this method
     * because RSS feeds deliver it as pre-encoded HTML (e.g. {@code &#8217;})
     * or as literal HTML markup (e.g. Google News {@code <a>} elements), both
     * of which render correctly in a browser without further escaping.
     *
     * @param text Raw plain-text string; {@code null} returns an empty string.
     * @return HTML-safe string.
     */
    private String escapeHtml(String text) {
        log.debug("escapeHtml() | text length={}", text == null ? 0 : text.length());
        if (text == null) {
            log.debug("escapeHtml() | return=empty (null input)");
            return "";
        }
        String result = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        log.debug("escapeHtml() | return={} chars", result.length());
        return result;
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
