package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyDiscoveryResult;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.DiscoverCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyScrapingPort;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain service orchestrating the company discovery pipeline:
 * scrape startup directories, classify by subcategory, deduplicate,
 * persist as {@link NewsArticle} records, and generate newsletter markdown.
 *
 * <p>This service has no Spring dependencies — it is wired via
 * {@code AppConfig} and operates entirely on domain types and ports.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
public class CompanyDiscoveryService implements DiscoverCompaniesUseCase {

    private static final String TOPIC = "New AI Healthcare Companies";

    private final CompanyScrapingPort scrapingPort;
    private final ArticleStoragePort storagePort;
    private final CompanyClassifier classifier;
    private final CompanyDeduplicator deduplicator;
    private final CompanyNewsletterRenderer renderer;

    public CompanyDiscoveryService(CompanyScrapingPort scrapingPort,
                                   ArticleStoragePort storagePort,
                                   CompanyClassifier classifier,
                                   CompanyDeduplicator deduplicator,
                                   CompanyNewsletterRenderer renderer) {
        this.scrapingPort = scrapingPort;
        this.storagePort = storagePort;
        this.classifier = classifier;
        this.deduplicator = deduplicator;
        this.renderer = renderer;
    }

    @Override
    public CompanyDiscoveryResult discover() {
        // 1. Scrape all sources (YC + TopStartups + anchors)
        List<Company> raw = scrapingPort.scrapeAll();
        int totalScraped = raw.size();

        // 2. Classify — apply tags and AI/health flags
        List<Company> classified = classifier.classifyAll(raw);

        // 3. Deduplicate
        List<Company> deduped = deduplicator.dedupe(classified);
        int afterDedup = deduped.size();

        // 4. Filter to AI + Health
        List<Company> filtered = new ArrayList<>();
        for (Company c : deduped) {
            if (c.isAI() && c.isHealth()) {
                filtered.add(c);
            }
        }
        int aiHealthFiltered = filtered.size();

        // 5. Persist as NewsArticle records
        List<NewsArticle> articles = new ArrayList<>();
        for (Company c : filtered) {
            articles.add(toNewsArticle(c));
        }
        storagePort.save(articles);

        // 6. Generate newsletter markdown
        String markdown = renderer.renderMarkdown(filtered);

        return new CompanyDiscoveryResult(filtered, markdown, totalScraped, afterDedup, aiHealthFiltered);
    }

    private NewsArticle toNewsArticle(Company company) {
        String articleId = "startup-" + company.name().toLowerCase().replaceAll("[^a-z0-9]", "-");

        URI url;
        if (company.companySite() != null && !company.companySite().isBlank()) {
            url = URI.create(company.companySite());
        } else if (company.url() != null && !company.url().isBlank()) {
            url = URI.create(company.url());
        } else {
            url = URI.create("https://unknown.example/" + articleId);
        }

        return new NewsArticle(
                articleId,
                company.name(),
                url,
                company.description(),
                TOPIC,
                null,
                null,
                company.source(),
                "INDUSTRY",
                0.6,
                Instant.now()
        );
    }
}
