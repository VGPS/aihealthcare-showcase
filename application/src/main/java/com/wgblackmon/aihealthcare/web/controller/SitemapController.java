package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a dynamic XML sitemap for search engine indexing.
 *
 * <p>Includes all public pages (insights, wiki, directory, legislation,
 * pricing, about, privacy) and auto-discovers new {@code /insights/*.html}
 * files so they are indexed without manual sitemap edits.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-17
 * @updated 2026-09-17
 */
@Slf4j
@Controller
public class SitemapController {

    private static final String BASE_URL = "https://app.bigskylabs.ai";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        log.debug("sitemap() | generating sitemap.xml");

        String today = LocalDate.now().format(DATE_FMT);

        List<SitemapEntry> entries = new ArrayList<>();

        entries.add(new SitemapEntry("/", "daily", "1.0"));
        entries.add(new SitemapEntry("/pricing", "monthly", "0.8"));
        entries.add(new SitemapEntry("/about", "monthly", "0.7"));
        entries.add(new SitemapEntry("/privacy", "yearly", "0.3"));
        entries.add(new SitemapEntry("/directory", "daily", "0.9"));
        entries.add(new SitemapEntry("/wiki", "daily", "0.9"));
        entries.add(new SitemapEntry("/legislation", "weekly", "0.8"));
        entries.add(new SitemapEntry("/login", "yearly", "0.2"));
        entries.add(new SitemapEntry("/register", "yearly", "0.3"));

        List<String> insightPages = discoverInsightPages();
        for (String page : insightPages) {
            entries.add(new SitemapEntry("/insights/" + page, "monthly", "0.7"));
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        for (SitemapEntry entry : entries) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(BASE_URL).append(entry.path).append("</loc>\n");
            xml.append("    <lastmod>").append(today).append("</lastmod>\n");
            xml.append("    <changefreq>").append(entry.changefreq).append("</changefreq>\n");
            xml.append("    <priority>").append(entry.priority).append("</priority>\n");
            xml.append("  </url>\n");
        }

        xml.append("</urlset>\n");

        String result = xml.toString();
        log.debug("sitemap() | return={} entries", entries.size());
        return result;
    }

    private List<String> discoverInsightPages() {
        List<String> pages = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:static/insights/*.html");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    pages.add(filename);
                }
            }
        } catch (IOException e) {
            log.warn("discoverInsightPages() | failed to scan insights directory: {}", e.getMessage());
        }
        return pages;
    }

    private record SitemapEntry(String path, String changefreq, String priority) {}
}
