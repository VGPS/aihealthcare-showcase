package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Public insights hub — lists all static insight articles from
 * {@code classpath:static/insights/*.html} with title, date, and summary.
 *
 * <p>Articles are static HTML files served directly by Spring's resource handler
 * at {@code /insights/{filename}}. This controller provides the hub index page
 * at {@code GET /insights/} so Google can discover all articles via internal links.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-09-30
 * @updated 2026-09-30 — T9 SEO: loadInsights() package-visible for HomeController reuse
 */
@Slf4j
@Controller
@RequestMapping("/insights")
public class InsightsController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    /**
     * Renders the public insights hub listing all articles.
     */
    @GetMapping({"", "/"})
    public String hub(Model model) {
        log.debug("hub()");

        List<InsightEntry> articles = loadInsights();
        model.addAttribute("articles", articles);
        model.addAttribute("pageDescription",
                "Analysis and intelligence on AI in healthcare — regulatory trends, market moves, and platform developments. Free to read.");

        log.debug("hub() | return=insights, articles={}", articles.size());
        return "insights";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Package-visible so {@link HomeController} can reuse the classpath scan for its "Latest Insights" list. */
    List<InsightEntry> loadInsights() {
        List<InsightEntry> entries = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:static/insights/*.html");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;
                InsightEntry entry = parseEntry(resource, filename);
                if (entry != null) entries.add(entry);
            }
        } catch (IOException e) {
            log.warn("loadInsights() | failed to scan insights: {}", e.getMessage());
        }
        entries.sort(Comparator.comparing(InsightEntry::date).reversed());
        log.debug("loadInsights() | return={} entries", entries.size());
        return entries;
    }

    private InsightEntry parseEntry(Resource resource, String filename) {
        String date = filename.length() >= 10 ? filename.substring(0, 10) : "";
        String displayDate = formatDisplayDate(date);
        String title = filename;
        String description = "";
        try (InputStream is = resource.getInputStream()) {
            Document doc = Jsoup.parse(is, "UTF-8", "");
            String rawTitle = doc.title();
            // Strip " | BigSkyLabs" suffix if present
            title = rawTitle.contains(" | ") ? rawTitle.substring(0, rawTitle.lastIndexOf(" | ")) : rawTitle;
            String ogDesc = doc.select("meta[property=og:description]").attr("content");
            description = ogDesc.isBlank() ? doc.select("meta[name=description]").attr("content") : ogDesc;
        } catch (IOException e) {
            log.warn("parseEntry() | could not read {}: {}", filename, e.getMessage());
        }
        return new InsightEntry(filename, title, description, date, displayDate);
    }

    private static String formatDisplayDate(String isoDate) {
        if (isoDate.length() < 10) return "";
        try {
            return LocalDate.parse(isoDate).format(DISPLAY_FMT);
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    /**
     * Lightweight insight article descriptor for the hub page.
     */
    public record InsightEntry(
            String filename,
            String title,
            String description,
            String date,
            String displayDate) {
    }
}
