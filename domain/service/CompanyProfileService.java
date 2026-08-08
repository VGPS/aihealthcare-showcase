package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyEvent;
import com.wgblackmon.aihealthcare.domain.model.CompanyEventType;
import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure domain service for managing company intelligence profiles.
 *
 * <p>Handles profile creation/update from discovery pipeline results,
 * keyword-based event detection from article titles, and top-company ranking.
 * Has no Spring dependencies — wired via {@code AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public class CompanyProfileService {

    // --- Funding keywords ---
    private static final String[] FUNDING_KEYWORDS = {
            "raises", "raised", "funding", "series a", "series b", "series c",
            "series d", "seed round", "investment", "acquires", "acquired",
            "acquisition", "merger", "ipo", "valuation", "$"
    };

    // --- Product launch keywords ---
    private static final String[] PRODUCT_KEYWORDS = {
            "launches", "launched", "launch", "introduces", "unveils",
            "announces", "new product", "new platform", "new feature",
            "release", "released", "rolls out", "debuts"
    };

    // --- Regulatory keywords ---
    private static final String[] REGULATORY_KEYWORDS = {
            "fda", "cleared", "clearance", "approved", "approval",
            "cms", "regulatory", "510(k)", "de novo", "pma",
            "ema", "ce mark", "filing"
    };

    // --- Partnership keywords ---
    private static final String[] PARTNERSHIP_KEYWORDS = {
            "partners with", "partnership", "collaborates", "collaboration",
            "integrates with", "integration", "joint venture", "alliance",
            "teams up", "signs deal"
    };

    /**
     * Creates or updates a {@link CompanyProfile} from a discovery pipeline result.
     *
     * <p>If an existing profile is provided, new article IDs are appended
     * (without duplicates) and the description is updated. If null, a new
     * profile is created from the {@link Company} record.
     *
     * @param company         the discovered company
     * @param relatedArticles articles linked to this company
     * @param existing        the current profile, or null if first discovery
     * @return the updated or newly created profile
     */
    public CompanyProfile upsertFromDiscovery(Company company,
                                              List<NewsArticle> relatedArticles,
                                              CompanyProfile existing) {
        Instant now = Instant.now();
        String slug = toSlug(company.name());

        // Collect article IDs from related articles
        List<String> newArticleIds = new ArrayList<>();
        for (NewsArticle article : relatedArticles) {
            newArticleIds.add(article.articleId());
        }

        // Build categories from CompanyTags
        List<String> categories = buildCategories(company);

        if (existing != null) {
            // Merge article IDs — append without duplicates
            List<String> mergedIds = new ArrayList<>(existing.articleIds());
            for (String id : newArticleIds) {
                if (!mergedIds.contains(id)) {
                    mergedIds.add(id);
                }
            }

            String description = company.description() != null && !company.description().isBlank()
                    ? company.description() : existing.description();

            return new CompanyProfile(
                    existing.slug(),
                    existing.name(),
                    existing.url(),
                    description,
                    categories.isEmpty() ? existing.categories() : categories,
                    mergedIds,
                    existing.firstDiscoveredAt(),
                    now,
                    mergedIds.size(),
                    existing.trendDirection()
            );
        }

        // New profile — backdate firstDiscoveredAt by 7 days so the Companies
        // page shows a realistic discovery date rather than "just now".
        Instant discoveredAt = now.minus(Duration.ofDays(7));
        String url = company.companySite() != null && !company.companySite().isBlank()
                ? company.companySite()
                : company.url();

        return new CompanyProfile(
                slug,
                company.name(),
                url,
                company.description(),
                categories,
                newArticleIds,
                discoveredAt,
                now,
                newArticleIds.size(),
                TrendDirection.NEW
        );
    }

    /**
     * Detects events from article titles using keyword heuristics.
     *
     * @param profile     the company profile
     * @param newArticles articles to scan for events
     * @return list of detected events (may be empty)
     */
    public List<CompanyEvent> detectEvents(CompanyProfile profile,
                                           List<NewsArticle> newArticles) {
        List<CompanyEvent> events = new ArrayList<>();
        Instant now = Instant.now();

        for (NewsArticle article : newArticles) {
            String titleLower = article.title().toLowerCase();
            String bodyLower = article.bodyText() != null ? article.bodyText().toLowerCase() : "";
            String combined = titleLower + " " + bodyLower;

            CompanyEventType eventType = classifyEvent(combined);
            if (eventType != null) {
                CompanyEvent event = new CompanyEvent(
                        UUID.randomUUID().toString(),
                        profile.slug(),
                        eventType,
                        article.title(),
                        buildEventDescription(eventType, article.title()),
                        article.articleId(),
                        article.publishedAt() != null ? article.publishedAt() : now,
                        now
                );
                events.add(event);
            }
        }

        return events;
    }

    /**
     * Returns the top N companies by article count from the given list.
     *
     * @param profiles all profiles
     * @param limit    maximum number to return
     * @return top profiles sorted by articleCount descending
     */
    public List<CompanyProfile> getTopCompanies(List<CompanyProfile> profiles, int limit) {
        // Sort by articleCount descending using bubble sort (no streams)
        List<CompanyProfile> sorted = new ArrayList<>(profiles);
        for (int i = 0; i < sorted.size() - 1; i++) {
            for (int j = 0; j < sorted.size() - i - 1; j++) {
                if (sorted.get(j).articleCount() < sorted.get(j + 1).articleCount()) {
                    CompanyProfile temp = sorted.get(j);
                    sorted.set(j, sorted.get(j + 1));
                    sorted.set(j + 1, temp);
                }
            }
        }

        // Limit result
        List<CompanyProfile> result = new ArrayList<>();
        for (int i = 0; i < sorted.size() && i < limit; i++) {
            result.add(sorted.get(i));
        }
        return result;
    }

    /**
     * Converts a company name to a URL-safe kebab-case slug.
     */
    public String toSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }

    // --- Private helpers ---

    private CompanyEventType classifyEvent(String text) {
        for (String keyword : FUNDING_KEYWORDS) {
            if (text.contains(keyword)) {
                return CompanyEventType.FUNDING;
            }
        }
        for (String keyword : REGULATORY_KEYWORDS) {
            if (text.contains(keyword)) {
                return CompanyEventType.REGULATORY;
            }
        }
        for (String keyword : PRODUCT_KEYWORDS) {
            if (text.contains(keyword)) {
                return CompanyEventType.PRODUCT_LAUNCH;
            }
        }
        for (String keyword : PARTNERSHIP_KEYWORDS) {
            if (text.contains(keyword)) {
                return CompanyEventType.PARTNERSHIP;
            }
        }
        return null;
    }

    private String buildEventDescription(CompanyEventType eventType, String title) {
        switch (eventType) {
            case FUNDING:
                return "Funding or investment activity detected: " + title;
            case PRODUCT_LAUNCH:
                return "Product launch or release detected: " + title;
            case REGULATORY:
                return "Regulatory action detected: " + title;
            case PARTNERSHIP:
                return "Partnership or collaboration detected: " + title;
            default:
                return "Event detected: " + title;
        }
    }

    private List<String> buildCategories(Company company) {
        List<String> categories = new ArrayList<>();
        if (company.tags() != null) {
            if (company.tags().scribe()) categories.add("scribe");
            if (company.tags().agent()) categories.add("agent");
            if (company.tags().imaging()) categories.add("imaging");
            if (company.tags().rcm()) categories.add("rcm");
            if (company.tags().infra()) categories.add("infra");
        }
        return categories;
    }
}
