package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.persistence.HealthcareAiCompanyEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.HealthcareAiCompanyRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.StateLawEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.StateLawRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serves robots.txt and sitemap.xml for search engine crawlers.
 *
 * <p>The sitemap is built dynamically from the company directory, state
 * legislation registry, and wiki repositories. It is cached for 10 minutes
 * via the {@code sitemap} Caffeine cache to avoid hitting the database on
 * every crawler request.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-10
 * @updated 2026-09-10
 */
@Slf4j
@Controller
public class SeoController {

    private final String baseUrl;
    private final HealthcareAiCompanyRepository companyRepository;
    private final StateLawRepository stateLawRepository;
    private final WikiPageRepository wikiPageRepository;

    public SeoController(@Value("${aihealthcare.base-url}") String baseUrl,
                          HealthcareAiCompanyRepository companyRepository,
                          StateLawRepository stateLawRepository,
                          WikiPageRepository wikiPageRepository) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.companyRepository = companyRepository;
        this.stateLawRepository = stateLawRepository;
        this.wikiPageRepository = wikiPageRepository;
        log.debug("SeoController() | baseUrl={}", this.baseUrl);
    }

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
                Allow: /about
                Allow: /pricing
                Allow: /developer
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
        log.debug("robotsTxt() | return=(robots.txt content)");
        return result;
    }

    /**
     * Generates and caches a sitemap.xml containing all public pages:
     * static pages, company directory entries, legislation entries, and wiki pages.
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    @Cacheable("sitemap")
    public String sitemapXml() {
        log.debug("sitemapXml()");

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        addUrl(sb, "/", "1.0", "daily", today);
        addUrl(sb, "/about", "0.7", "monthly", today);
        addUrl(sb, "/pricing", "0.7", "monthly", today);
        addUrl(sb, "/developer", "0.6", "monthly", today);
        addUrl(sb, "/directory", "0.9", "daily", today);
        addUrl(sb, "/legislation", "0.9", "daily", today);
        addUrl(sb, "/wiki", "0.8", "daily", today);

        List<HealthcareAiCompanyEntity> companies = companyRepository.findAll();
        for (HealthcareAiCompanyEntity company : companies) {
            addUrl(sb, "/directory/" + company.getSlug(), "0.6", "weekly", today);
        }

        List<StateLawEntity> laws = stateLawRepository.findAll();
        for (StateLawEntity law : laws) {
            addUrl(sb, "/legislation/" + law.getId(), "0.6", "weekly", today);
        }

        List<WikiPageEntity> wikiPages = wikiPageRepository.findAll();
        for (WikiPageEntity page : wikiPages) {
            addUrl(sb, "/wiki/" + page.getSlug(), "0.5", "weekly", today);
        }

        sb.append("</urlset>\n");

        String result = sb.toString();
        log.debug("sitemapXml() | return=sitemap with {} URLs",
                  7 + companies.size() + laws.size() + wikiPages.size());
        return result;
    }

    private void addUrl(StringBuilder sb, String path, String priority,
                        String changefreq, String lastmod) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(escapeXml(baseUrl + path)).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
