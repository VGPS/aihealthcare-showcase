package com.wgblackmon.aihealthcare.infrastructure.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TopicSummary;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TopicSummaryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates human-readable comparison reports in the configured output directory
 * (default: {@code ./linted/}).
 *
 * <p><b>Phase 1 — lint baseline</b> ({@link #runLintBaseline()}): reads every persisted
 * topic summary, runs the {@link SlopLinter} on the existing text, and writes a markdown
 * file showing each summary's text, score, and lint findings.  No LLM calls.
 *
 * <p><b>Phase 2 — before/after</b> ({@link #runBeforeAfter(int)}): for each topic that
 * has a persisted summary, fetches the underlying articles, runs the full
 * extraction + writer pipeline, lints both the old and new text, and writes a
 * side-by-side markdown file so the quality difference is immediately visible.
 *
 * <p>Both methods are safe to call repeatedly — output files are named with a
 * timestamp so previous reports are never overwritten.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-29
 * @updated 2026-09-29
 */
@Slf4j
@Component
public class SummaryCompareReportService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH-mm-ss")
            .withZone(ZoneOffset.UTC);
    private static final int TARGET_WORDS = 180;

    private final TopicSummaryPort    topicSummaryPort;
    private final ArticleIngestionPort articleIngestionPort;
    private final ExtractionService   extractionService;
    private final SummaryWriter       summaryWriter;
    private final ObjectMapper        mapper;
    private final SlopLinter          linter;
    private final String              outputDir;

    public SummaryCompareReportService(
            TopicSummaryPort topicSummaryPort,
            ArticleIngestionPort articleIngestionPort,
            ExtractionService extractionService,
            SummaryWriter summaryWriter,
            ObjectMapper mapper,
            SlopLinter linter,
            @Value("${summary.report.output-dir:./linted}") String outputDir) {
        this.topicSummaryPort    = topicSummaryPort;
        this.articleIngestionPort = articleIngestionPort;
        this.extractionService   = extractionService;
        this.summaryWriter       = summaryWriter;
        this.mapper              = mapper;
        this.linter              = linter;
        this.outputDir           = outputDir;
    }

    // -------------------------------------------------------------------------
    // Phase 1: lint baseline — no LLM calls
    // -------------------------------------------------------------------------

    /**
     * Lints all existing topic summaries and writes a baseline report to
     * {@code <outputDir>/lint-baseline-<timestamp>.md}.
     *
     * @return the path of the written report file
     */
    public Path runLintBaseline() throws IOException {
        log.debug("runLintBaseline() | starting");

        List<TopicSummary> all = topicSummaryPort.findAll();
        log.info("runLintBaseline() | found {} topic summaries to lint", all.size());

        Path dir  = ensureOutputDir();
        Path file = dir.resolve("lint-baseline-" + TS_FMT.format(Instant.now()) + ".md");

        StringBuilder sb = buildLintBaselineReport(all);
        Files.writeString(file, sb.toString());
        log.info("runLintBaseline() | written to {}", file);

        log.debug("runLintBaseline() | return={}", file);
        return file;
    }

    private StringBuilder buildLintBaselineReport(List<TopicSummary> summaries) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Lint Baseline Report\n");
        sb.append("Generated: ").append(Instant.now()).append("  \n");
        sb.append("Source: `topic_summaries` table  \n");
        sb.append("Entries linted: ").append(summaries.size()).append("\n\n");

        int passCount = 0, blockCount = 0, warnCount = 0;
        List<String> blockTopics = new ArrayList<>();

        for (TopicSummary ts : summaries) {
            SlopLinter.LintResult result = linter.lint(ts.summaryText(), Set.of());
            if (result.passes()) passCount++;
            else if (result.hasBlocks()) { blockCount++; blockTopics.add(ts.topic()); }
            else warnCount++;

            sb.append("---\n\n");
            sb.append("## Topic: `").append(ts.topic()).append("`\n");
            sb.append("**Lint Score:** ").append(result.score()).append("/100  \n");
            sb.append("**Status:** ").append(result.statusLabel()).append("  \n");
            if (!result.findings().isEmpty()) {
                sb.append("**Findings:**\n");
                for (SlopLinter.Finding f : result.findings()) {
                    sb.append("- [").append(f.severity()).append("] ")
                      .append(f.rule()).append(": ").append(f.detail()).append("\n");
                }
            }
            sb.append("\n### BEFORE (Original Text)\n\n");
            sb.append("> ").append(ts.summaryText().replace("\n", "\n> ")).append("\n\n");
        }

        // Summary table at end
        sb.insert(sb.indexOf("\n\n") + 2,
                "| Status | Count |\n|--------|-------|\n" +
                "| PASS   | " + passCount + " |\n" +
                "| BLOCKED | " + blockCount + " |\n" +
                "| WARN-only | " + warnCount + " |\n\n" +
                (blockTopics.isEmpty() ? "" :
                        "**Blocked topics:** " + String.join(", ", blockTopics) + "\n\n")
        );

        return sb;
    }

    // -------------------------------------------------------------------------
    // Phase 2: before/after with full pipeline
    // -------------------------------------------------------------------------

    /**
     * Runs the full extraction + writer pipeline on topic summaries and writes a
     * before/after comparison report to {@code <outputDir>/before-after-<timestamp>.md}.
     *
     * @param maxTopics maximum number of topics to process (use a small number for testing)
     * @return the path of the written report file
     */
    public Path runBeforeAfter(int maxTopics) throws IOException {
        log.debug("runBeforeAfter() | maxTopics={}", maxTopics);

        List<TopicSummary> all = topicSummaryPort.findAll();
        List<TopicSummary> batch = all.stream().limit(maxTopics).collect(Collectors.toList());
        log.info("runBeforeAfter() | processing {} of {} topic summaries", batch.size(), all.size());

        Path dir  = ensureOutputDir();
        Path file = dir.resolve("before-after-" + TS_FMT.format(Instant.now()) + ".md");

        StringBuilder sb = new StringBuilder();
        sb.append("# Before / After Summary Comparison\n");
        sb.append("Generated: ").append(Instant.now()).append("  \n");
        sb.append("Pipeline: anti-slop-v1  \n");
        sb.append("Topics processed: ").append(batch.size()).append("\n\n");

        for (TopicSummary ts : batch) {
            appendBeforeAfterEntry(sb, ts);
        }

        Files.writeString(file, sb.toString());
        log.info("runBeforeAfter() | written to {}", file);

        log.debug("runBeforeAfter() | return={}", file);
        return file;
    }

    private void appendBeforeAfterEntry(StringBuilder sb, TopicSummary ts) {
        sb.append("---\n\n");
        sb.append("## Topic: `").append(ts.topic()).append("`\n\n");

        // --- BEFORE ---
        SlopLinter.LintResult beforeResult = linter.lint(ts.summaryText(), Set.of());
        sb.append("### BEFORE (Original)\n");
        sb.append("**Score:** ").append(beforeResult.score()).append("/100 · **Status:** ")
          .append(beforeResult.statusLabel()).append("  \n");
        appendFindings(sb, beforeResult);
        sb.append("\n").append(ts.summaryText().trim()).append("\n\n");

        // --- AFTER ---
        sb.append("### AFTER (Anti-Slop Pipeline)\n");
        try {
            List<NewsArticle> articles = articleIngestionPort.fetchAllByTopic(ts.topic());
            if (articles.isEmpty()) {
                sb.append("_No source articles found for this topic — skipping pipeline run._\n\n");
                return;
            }

            List<SourceDoc> sources = buildSourceDocs(articles);
            Set<String> validIds    = sources.stream().map(SourceDoc::citeId).collect(Collectors.toSet());

            SummaryExtraction extraction = extractionService.extract("TOPIC_SUMMARY", ts.topic(), sources);
            String afterText             = summaryWriter.write("TOPIC_SUMMARY", extraction, TARGET_WORDS);
            SlopLinter.LintResult afterResult = linter.lint(afterText, validIds);

            sb.append("**Score:** ").append(afterResult.score()).append("/100 · **Status:** ")
              .append(afterResult.statusLabel()).append("  \n");
            appendFindings(sb, afterResult);
            sb.append("\n").append(afterText.trim()).append("\n\n");

            // Extraction JSON for inspection
            sb.append("<details>\n<summary>Extraction JSON (Phase 2 intermediate)</summary>\n\n```json\n");
            sb.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(extraction));
            sb.append("\n```\n\n</details>\n\n");

        } catch (Exception e) {
            log.error("runBeforeAfter() | pipeline failed for topic={}", ts.topic(), e);
            sb.append("_Pipeline error: ").append(e.getMessage()).append("_\n\n");
        }
    }

    private List<SourceDoc> buildSourceDocs(List<NewsArticle> articles) {
        // Take top 8 by source weight, falling back to order returned by DB
        List<NewsArticle> selected = articles.stream()
                .sorted(Comparator.comparingDouble(NewsArticle::sourceWeight).reversed())
                .limit(8)
                .collect(Collectors.toList());

        List<SourceDoc> docs = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            NewsArticle a = selected.get(i);
            String content = a.bodyText() != null && !a.bodyText().isBlank()
                    ? a.bodyText().substring(0, Math.min(a.bodyText().length(), 1500))
                    : a.title();
            LocalDate pub = a.publishedAt() != null
                    ? a.publishedAt().atZone(ZoneOffset.UTC).toLocalDate()
                    : null;
            docs.add(new SourceDoc(
                    "S" + (i + 1),
                    SourceDoc.SourceType.NEWS_ARTICLE,
                    a.title(),
                    a.url() != null ? a.url().toString() : null,
                    "article:" + a.articleId(),
                    pub,
                    content
            ));
        }
        return docs;
    }

    private void appendFindings(StringBuilder sb, SlopLinter.LintResult result) {
        if (!result.findings().isEmpty()) {
            for (SlopLinter.Finding f : result.findings()) {
                sb.append("- [").append(f.severity()).append("] ")
                  .append(f.rule()).append(": ").append(f.detail()).append("  \n");
            }
        }
    }

    private Path ensureOutputDir() throws IOException {
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        return dir;
    }
}
