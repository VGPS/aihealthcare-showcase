package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.model.WikiBundleExportResult;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports the LLM-compiled wiki knowledge base as an OKF v0.2 bundle — a directory
 * of markdown files with YAML frontmatter consumable by any tool that reads plain
 * markdown (Obsidian, MkDocs, Hugo, raw git, etc.).
 *
 * <p>Each {@link WikiPage} becomes one {@code .md} file under a subdirectory named
 * after its {@code pageType} (ENTITY, CONCEPT, COMPARISON, OVERVIEW). Contradictions
 * are written to a {@code CONTRADICTIONS/} subdirectory. An {@code index.md} is
 * generated at both the bundle root and each type subdirectory for progressive
 * disclosure (an agent or human can navigate one level at a time).
 *
 * <p>The OKF frontmatter fields used:
 * <ul>
 *   <li>{@code type} — {@code "AI Healthcare Wiki Page"} qualified with the wiki page type</li>
 *   <li>{@code title}, {@code description} (first sentence of content)</li>
 *   <li>{@code resource} — link to the live wiki page at bigskylabs.ai</li>
 *   <li>{@code tags}</li>
 *   <li>{@code generated} — {@code {by: WikiCompilationAdapter, at: <updatedAt>}}</li>
 *   <li>{@code status} — {@code current} or {@code stale} (content older than 90 days)</li>
 *   <li>{@code stale_after} — 90 days after {@code updatedAt}</li>
 *   <li>{@code sources} — one entry per {@link SourceRef}</li>
 *   <li>{@code revision}</li>
 * </ul>
 *
 * <p>No LLM calls are made — this is pure serialization of data already in the DB.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-07
 * @updated 2026-11-07
 */
@Slf4j
@Component
public class OkfBundleExporterAdapter {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;
    private static final long STALE_DAYS = 90L;

    private final WikiQueryPort wikiQueryPort;
    private final String exportDir;

    public OkfBundleExporterAdapter(WikiQueryPort wikiQueryPort,
                                     @Value("${aihealthcare.wiki.okf-export-dir:${user.home}/wiki-bundle}") String exportDir) {
        log.debug("OkfBundleExporterAdapter() | exportDir={}", exportDir);
        this.wikiQueryPort = wikiQueryPort;
        this.exportDir = exportDir;
    }

    /**
     * Exports all wiki pages and contradictions to the configured bundle directory.
     *
     * <p>Blank-query call to {@link WikiQueryPort#findRelevantPages} returns all pages
     * (the documented W7 contract). Contradictions are retrieved since the epoch to
     * capture the full history.
     *
     * @return export result with page count, contradiction count, and bundle directory path
     */
    public WikiBundleExportResult exportBundle() {
        log.debug("exportBundle() | exportDir={}", exportDir);

        List<WikiPage> pages = wikiQueryPort.findRelevantPages("", Integer.MAX_VALUE);
        List<Contradiction> contradictions = wikiQueryPort.recentContradictions(Instant.EPOCH);

        log.info("exportBundle() | exporting {} pages, {} contradictions to {}", pages.size(), contradictions.size(), exportDir);

        Path root = Paths.get(exportDir);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create bundle directory: " + root, e);
        }

        Map<String, List<WikiPage>> byType = groupByType(pages);
        int pageCount = 0;

        for (Map.Entry<String, List<WikiPage>> entry : byType.entrySet()) {
            String typeName = entry.getKey();
            List<WikiPage> group = entry.getValue();
            Path typeDir = root.resolve(typeName);
            try {
                Files.createDirectories(typeDir);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot create type directory: " + typeDir, e);
            }

            for (WikiPage page : group) {
                writePageFile(typeDir, page);
                pageCount++;
            }
            writeTypeIndex(typeDir, typeName, group);
        }

        int contradictionCount = 0;
        if (!contradictions.isEmpty()) {
            Path contraDir = root.resolve("CONTRADICTIONS");
            try {
                Files.createDirectories(contraDir);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot create contradictions directory: " + contraDir, e);
            }
            for (Contradiction c : contradictions) {
                writeContradictionFile(contraDir, c);
                contradictionCount++;
            }
            writeContradictionsIndex(contraDir, contradictions);
        }

        writeRootIndex(root, byType, contradictionCount);

        WikiBundleExportResult result = new WikiBundleExportResult(pageCount, contradictionCount, root.toAbsolutePath().toString());
        log.info("exportBundle() | export complete: {} pages, {} contradictions -> {}", pageCount, contradictionCount, result.bundleDir());
        log.debug("exportBundle() | return={}", result);
        return result;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private Map<String, List<WikiPage>> groupByType(List<WikiPage> pages) {
        log.debug("groupByType() | pages={}", pages.size());
        Map<String, List<WikiPage>> map = new LinkedHashMap<>();
        for (WikiPage page : pages) {
            String key = page.pageType().name();
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(page);
        }
        log.debug("groupByType() | return={} types", map.size());
        return map;
    }

    private void writePageFile(Path typeDir, WikiPage page) {
        log.debug("writePageFile() | slug={}", page.slug());

        Instant updatedAt = page.updatedAt() != null ? page.updatedAt() : page.createdAt();
        Instant staleAfter = updatedAt.plusSeconds(STALE_DAYS * 86400L);
        boolean isStale = Instant.now().isAfter(staleAfter);
        String status = isStale ? "stale" : "current";

        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("type: AI Healthcare Wiki Page\n");
        sb.append("title: ").append(yamlString(page.title())).append("\n");
        sb.append("description: ").append(yamlString(firstSentence(page.contentMarkdown()))).append("\n");
        sb.append("resource: https://bigskylabs.ai/wiki/").append(page.slug()).append("\n");
        sb.append("tags: ").append(yamlList(page.tags())).append("\n");
        sb.append("generated:\n");
        sb.append("  by: WikiCompilationAdapter/claude-haiku-4-5\n");
        sb.append("  at: ").append(ISO.format(updatedAt)).append("\n");
        sb.append("status: ").append(status).append("\n");
        sb.append("stale_after: ").append(ISO.format(staleAfter)).append("\n");
        if (!page.sources().isEmpty()) {
            sb.append("sources:\n");
            for (SourceRef src : page.sources()) {
                sb.append("  - title: ").append(yamlString(src.sourceName())).append("\n");
                sb.append("    last_modified: ").append(src.harvestedOn().atStartOfDay().toInstant(ZoneOffset.UTC).toString()).append("\n");
                sb.append("    credibility: machine-generated\n");
                if (src.excerpt() != null && !src.excerpt().isBlank()) {
                    sb.append("    excerpt: ").append(yamlString(src.excerpt())).append("\n");
                }
            }
        }
        sb.append("revision: ").append(page.revision()).append("\n");
        sb.append("---\n\n");

        sb.append(page.contentMarkdown());

        if (!page.relatedSlugs().isEmpty()) {
            sb.append("\n\n## Related Pages\n\n");
            for (String relatedSlug : page.relatedSlugs()) {
                sb.append("- [").append(relatedSlug).append("](../").append(relatedSlug).append(".md)\n");
            }
        }

        Path filePath = typeDir.resolve(page.slug() + ".md");
        try {
            Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write page file: " + filePath, e);
        }

        log.debug("writePageFile() | return=void (wrote {})", filePath.getFileName());
    }

    private void writeContradictionFile(Path contraDir, Contradiction contradiction) {
        log.debug("writeContradictionFile() | pageSlug={}", contradiction.pageSlug());

        String filename = contradiction.pageSlug() + "-" + contradiction.detectedAt().getEpochSecond() + ".md";
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("type: Contradiction\n");
        sb.append("title: ").append(yamlString("Contradiction: " + contradiction.pageSlug())).append("\n");
        sb.append("description: ").append(yamlString("Prior claim reversed by new evidence on page " + contradiction.pageSlug())).append("\n");
        sb.append("tags: [contradiction, reversal-watch]\n");
        sb.append("generated:\n");
        sb.append("  by: WikiCompilationAdapter/claude-haiku-4-5\n");
        sb.append("  at: ").append(ISO.format(contradiction.detectedAt())).append("\n");
        sb.append("status: current\n");
        sb.append("---\n\n");
        sb.append("## Prior Claim\n\n").append(contradiction.priorClaim()).append("\n\n");
        sb.append("## New Claim\n\n").append(contradiction.newClaim()).append("\n\n");
        sb.append("**Detected:** ").append(contradiction.detectedAt()).append("\n\n");
        sb.append("See wiki page: [").append(contradiction.pageSlug()).append("](../ENTITY/")
                .append(contradiction.pageSlug()).append(".md)\n");

        Path filePath = contraDir.resolve(filename);
        try {
            Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write contradiction file: " + filePath, e);
        }

        log.debug("writeContradictionFile() | return=void");
    }

    private void writeTypeIndex(Path typeDir, String typeName, List<WikiPage> pages) {
        log.debug("writeTypeIndex() | typeName={}, pages={}", typeName, pages.size());

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(typeName).append(" Pages\n\n");
        sb.append("This directory contains ").append(pages.size())
                .append(" AI Healthcare wiki pages of type **").append(typeName).append("**.\n\n");
        sb.append("| Page | Description |\n");
        sb.append("|------|-------------|\n");
        for (WikiPage page : pages) {
            sb.append("| [").append(page.title()).append("](").append(page.slug()).append(".md) | ")
                    .append(firstSentence(page.contentMarkdown())).append(" |\n");
        }

        Path filePath = typeDir.resolve("index.md");
        try {
            Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write type index: " + filePath, e);
        }

        log.debug("writeTypeIndex() | return=void");
    }

    private void writeContradictionsIndex(Path contraDir, List<Contradiction> contradictions) {
        log.debug("writeContradictionsIndex() | contradictions={}", contradictions.size());

        StringBuilder sb = new StringBuilder();
        sb.append("# Contradictions (Reversal Watch)\n\n");
        sb.append(contradictions.size()).append(" contradictions detected in the AI Healthcare knowledge base.\n\n");
        sb.append("| Wiki Page | Detected |\n");
        sb.append("|-----------|----------|\n");
        for (Contradiction c : contradictions) {
            String filename = c.pageSlug() + "-" + c.detectedAt().getEpochSecond() + ".md";
            sb.append("| [").append(c.pageSlug()).append("](").append(filename).append(") | ")
                    .append(c.detectedAt().toString().substring(0, 10)).append(" |\n");
        }

        Path filePath = contraDir.resolve("index.md");
        try {
            Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write contradictions index: " + filePath, e);
        }

        log.debug("writeContradictionsIndex() | return=void");
    }

    private void writeRootIndex(Path root, Map<String, List<WikiPage>> byType, int contradictionCount) {
        log.debug("writeRootIndex() | types={}, contradictions={}", byType.size(), contradictionCount);

        int totalPages = byType.values().stream().mapToInt(List::size).sum();
        StringBuilder sb = new StringBuilder();
        sb.append("# AI Healthcare Wiki Bundle\n\n");
        sb.append("**Format:** Open Knowledge Format (OKF) v0.2  \n");
        sb.append("**Source:** [bigskylabs.ai/wiki](https://bigskylabs.ai/wiki)  \n");
        sb.append("**Generated:** ").append(Instant.now().toString().substring(0, 10)).append("  \n");
        sb.append("**Total pages:** ").append(totalPages).append("  \n\n");
        sb.append("## Sections\n\n");
        for (Map.Entry<String, List<WikiPage>> entry : byType.entrySet()) {
            sb.append("- [").append(entry.getKey()).append("](").append(entry.getKey()).append("/index.md)")
                    .append(" — ").append(entry.getValue().size()).append(" pages\n");
        }
        if (contradictionCount > 0) {
            sb.append("- [CONTRADICTIONS](CONTRADICTIONS/index.md) — ").append(contradictionCount).append(" entries\n");
        }
        sb.append("\n## About OKF\n\n");
        sb.append("Each `.md` file contains YAML frontmatter (`type`, `title`, `description`, `tags`, `generated`, `sources`, `status`) ");
        sb.append("followed by the page content in standard markdown. ");
        sb.append("Cross-links between pages use relative markdown links. ");
        sb.append("This bundle can be browsed in Obsidian, rendered with MkDocs, or loaded directly into an LLM context.\n");

        Path filePath = root.resolve("index.md");
        try {
            Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write root index: " + filePath, e);
        }

        log.debug("writeRootIndex() | return=void");
    }

    // ── YAML / text utilities ────────────────────────────────────────────────

    private String yamlString(String value) {
        if (value == null || value.isBlank()) {
            return "\"\"";
        }
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        if (escaped.contains(":") || escaped.contains("#") || escaped.contains("\n") || escaped.startsWith(" ")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String yamlList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(yamlString(items.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String firstSentence(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String stripped = markdown.replaceAll("#+\\s+", "").replaceAll("[*_`#]", "").trim();
        int dot = stripped.indexOf('.');
        int nl = stripped.indexOf('\n');
        int end = -1;
        if (dot >= 0 && nl >= 0) end = Math.min(dot, nl);
        else if (dot >= 0) end = dot;
        else if (nl >= 0) end = nl;
        if (end < 0 || end > 200) end = Math.min(stripped.length(), 200);
        else end = Math.min(end + 1, stripped.length());
        return stripped.substring(0, end).trim();
    }
}
