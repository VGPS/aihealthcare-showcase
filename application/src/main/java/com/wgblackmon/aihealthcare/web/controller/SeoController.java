package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.persistence.HealthcareAiCompanyEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.HealthcareAiCompanyRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.StateLawEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.StateLawRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.TrendSnapshotEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.TrendSnapshotRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Serves robots.txt, a sitemap index, and six per-section child sitemaps
 * for search engine crawlers.
 *
 * <p>The sitemap index at {@code /sitemap.xml} points to:
 * {@code /sitemap-pages.xml}, {@code /sitemap-wiki.xml},
 * {@code /sitemap-directory.xml}, {@code /sitemap-legislation.xml},
 * {@code /sitemap-trends.xml}, and {@code /sitemap-insights.xml}.
 *
 * <p>Every child sitemap uses real per-entity {@code <lastmod>} timestamps
 * so Google Search Console can report indexing progress per section.
 * {@code <changefreq>} and {@code <priority>} are omitted — Google ignores them.
 *
 * <p>All sitemaps are cached for 10 minutes via the {@code sitemap} Caffeine
 * cache (keyed by section name) to avoid hitting the database on every crawler
 * request.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-09-10
 * @updated 2026-09-30 — split into sitemap index + 6 children; real per-entity lastmod
 */
@Slf4j
@Controller
public class SeoController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final String baseUrl;
    private final HealthcareAiCompanyRepository companyRepository;
    private final StateLawRepository stateLawRepository;
    private final WikiPageRepository wikiPageRepository;
    private final TrendSnapshotRepository trendSnapshotRepository;
    private final String indexNowKey;

    public SeoController(@Value("${aihealthcare.base-url}") String baseUrl,
                          HealthcareAiCompanyRepository companyRepository,
                          StateLawRepository stateLawRepository,
                          WikiPageRepository wikiPageRepository,
                          TrendSnapshotRepository trendSnapshotRepository,
                          @Value("${aihealthcare.indexnow.key}") String indexNowKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.companyRepository = companyRepository;
        this.stateLawRepository = stateLawRepository;
        this.wikiPageRepository = wikiPageRepository;
        this.trendSnapshotRepository = trendSnapshotRepository;
        this.indexNowKey = indexNowKey;
        log.debug("SeoController() | baseUrl={}", this.baseUrl);
    }

    // ── IndexNow key verification ────────────────────────────────────────────

    /**
     * Serves the IndexNow key file at {@code GET /{key}.txt} so Bing can verify
     * domain ownership. Returns 404 for any filename that doesn't match the
     * configured key — this route only ever serves the one real key file.
     */
    @GetMapping(value = "/{key:[0-9a-f]+}.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String indexNowKeyFile(@PathVariable String key) {
        log.debug("indexNowKeyFile() | key={}", key);
        if (!indexNowKey.equals(key)) {
            log.debug("indexNowKeyFile() | return=404 (key mismatch)");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        log.debug("indexNowKeyFile() | return={}", indexNowKey);
        return indexNowKey;
    }

    // ── robots.txt ────────────────────────────────────────────────────────────

    /**
     * Serves robots.txt directing crawlers to public content and away from
     * authenticated pages.
     */
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robotsTxt() {
        log.debug("robotsTxt()");
        String result = """
                User-agent: *
                Allow: /
                Allow: /directory
                Allow: /directory/
                Allow: /wiki
                Allow: /wiki/
                Allow: /legislation
                Allow: /legislation/
                Allow: /trends
                Allow: /trends/
                Allow: /insights
                Allow: /insights/
                Allow: /about
                Allow: /pricing
                Allow: /developer
                Allow: /privacy
                Disallow: /dashboard
                Disallow: /admin
                Disallow: /api/
                Disallow: /monitoring/
                Disallow: /newsletter/
                Disallow: /watchlist
                Disallow: /enterprise/
                Disallow: /research/
                Disallow: /d/

                Sitemap: %s/sitemap.xml
                """.formatted(baseUrl);
        log.debug("robotsTxt() | return=void");
        return result;
    }

    // ── Sitemap index ─────────────────────────────────────────────────────────

    /**
     * Sitemap index — points to six per-section child sitemaps.
     * Cached for 10 minutes so crawlers don't hammer the DB.
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    @Cacheable(value = "sitemap", key = "'index'")
    public String sitemapIndex() {
        log.debug("sitemapIndex()");
        String today = LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String section : List.of("pages", "wiki", "directory", "legislation", "trends", "insights")) {
            sb.append("  <sitemap>\n");
            sb.append("    <loc>").append(baseUrl).append("/sitemap-").append(section).append(".xml</loc>\n");
            sb.append("    <lastmod>").append(today).append("</lastmod>\n");
            sb.append("  </sitemap>\n");
        }
        sb.append("</sitemapindex>\n");
        String result = sb.toString();
        log.debug("sitemapIndex() | return=sitemapindex (6 children)");
        return result;
    }

    // ── Child sitemaps ────────────────────────────────────────────────────────

    /**
     * Static pages sitemap — homepage, about, pricing, directory/wiki/legislation indexes.
     */
    @GetMapping(value = "/sitemap-pages.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    @Cacheable(value = "sitemap", key = "'pages'")
    public String sitemapPages() {
        log.debug("sitemapPages()");
        String today = LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
        StringBuilder sb = startUrlset();
        addUrl(sb, "/", today);
        addUrl(sb, "/about", today);
        addUrl(sb, "/pricing", today);
        addUrl(sb, "/developer", today);
        addUrl(sb, "/directory", today);
        addUrl(sb, "/wiki", today);
        addUrl(sb, "/legislation", today);
        addUrl(sb, "/insights/", today);
        addUrl(sb, "/privacy", today);
        sb.append("</urlset>\n");
        String result = sb.toString();
        log.debug("sitemapPages() | return=sitemap with 9 static pages");
        return result;
    }

    /**
     * Wiki pages sitemap — one entry per wiki page, lastmod from entity timestamp.
     */
    @GetMapping(value = "/sitemap-wiki.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    @Cacheable(value = "sitemap", key = "'wiki'")
    public String sitemapWiki() {
        log.debug("sitemapWiki()");
        List<WikiPageEntity> pages = wikiPageRepository.findAll();
        StringBuilder sb = startUrlset();
        for (WikiPageEntity page : pages) {
            Instant ts = page.getUpdatedAt() != null ? page.getUpdatedAt() : page.getCreatedAt();
            addUrl(sb, "/wiki/" + page.getSlug(), toDate(ts));
        }
        sb.append("</urlset>\n");
        String result = sb.toString();
        log.debug("sitemapWiki() | return=sitemap with {} wiki pages", pages.size());
        return result;
    }

    /**
     * Company directory sitemap — one entry per company, lastmod from entity timestamp.
     */
    @GetMapping(value = "/sitemap-directory.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    @Cacheable(value = "sitemap", key = "'directory'")
    public String sitemapDirectory() {
        log.debug("sitemapDirectory()");
        List<HealthcareAiCompanyEntity> companies = companyRepository.findAll();
        StringBuilder sb = startUrlset();
        for (HealthcareAiCompanyEntity company : companies) {
            Instant ts = company.getLastValidatedAt() != null
                    ? company.getLastValidatedAt() : company.getDiscoveredAt();
            addUrl(sb, "/directory/" + company.getSlug(), toDate(ts));
        }
        sb.append("</urlset>\n");
        String result = sb.toString();
        log.debug("sitemapDirectory() | return=sitemap with {} companies", companies.size());
        return result;
    }

    /**
     * State legislation sitemap — one entry per law, lastmod from entity timestamp.
     */
    @GetMapping(value = "/sitemap-legislation.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    @Cacheable(value = "sitemap", key = "'legislation'")
    public String sitemapLegislation() {
        log.debug("sitemapLegislation()");
        List<StateLawEntity> laws = stateLawRepository.findAll();
        StringBuilder sb = startUrlset();
        for (StateLawEntity law : laws) {
            addUrl(sb, "/legislation/" + law.getId(), toDate(law.getUpdatedAt()));
        }
        sb.append("</urlset>\n");
        String result = sb.toString();
        log.debug("sitemapLegislation() | return=sitemap with {} laws", laws.size());
        return result;
    }

    /**
     * Trends sitemap — one entry per snapshot date, lastmod from snapshot timestamp.
     * The bare {@code /trends} URL is excluded because it 302-redirects.
     */
    @GetMapping(value = "/sitemap-trends.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    @Cacheable(value = "sitemap", key = "'trends'")
    public String sitemapTrends() {
        log.debug("sitemapTrends()");
        List<TrendSnapshotEntity> snapshots = trendSnapshotRepository.findAllByOrderByGeneratedAtDesc();
        StringBuilder sb = startUrlset();
        for (TrendSnapshotEntity snapshot : snapshots) {
            String dateSlug = toDate(snapshot.getGeneratedAt());
            addUrl(sb, "/trends/" + dateSlug, dateSlug);
        }
        sb.append("</urlset>\n");
        String result = sb.toString();
        log.debug("sitemapTrends() | return=sitemap with {} trend snapshots", snapshots.size());
        return result;
    }

    /**
     * Insights sitemap — one entry per static HTML file, lastmod parsed from filename date.
     */
    @GetMapping(value = "/sitemap-insights.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    @Cacheable(value = "sitemap", key = "'insights'")
    public String sitemapInsights() {
        log.debug("sitemapInsights()");
        List<String[]> pages = discoverInsightPages();
        StringBuilder sb = startUrlset();
        for (String[] page : pages) {
            addUrl(sb, "/insights/" + page[0], page[1]);
        }
        sb.append("</urlset>\n");
        String result = sb.toString();
        log.debug("sitemapInsights() | return=sitemap with {} insight pages", pages.size());
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Discovers insight HTML files and returns pairs of [filename, lastmod-date].
     * Lastmod is parsed from the YYYY-MM-DD prefix of the filename.
     */
    private List<String[]> discoverInsightPages() {
        List<String[]> pages = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:static/insights/*.html");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;
                String lastmod = filename.length() >= 10 ? filename.substring(0, 10) : LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
                pages.add(new String[]{filename, lastmod});
            }
        } catch (IOException e) {
            log.warn("discoverInsightPages() | failed to scan insights directory: {}", e.getMessage());
        }
        return pages;
    }

    private StringBuilder startUrlset() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        return sb;
    }

    private void addUrl(StringBuilder sb, String path, String lastmod) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(escapeXml(baseUrl + path)).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("  </url>\n");
    }

    private static String toDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate().format(DATE_FMT);
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
