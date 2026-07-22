package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyEvent;
import com.wgblackmon.aihealthcare.domain.model.CompanyEventType;
import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.CompanyTags;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CompanyProfileService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class CompanyProfileServiceTest {

    private CompanyProfileService service;

    @BeforeEach
    void setUp() {
        service = new CompanyProfileService();
    }

    // --- upsertFromDiscovery tests ---

    @Test
    void upsertCreatesNewProfileWhenNoExisting() {
        Company company = new Company("Tempus AI", "YC", "https://yc.com/tempus",
                "https://tempus.com", "Clinical data platform", CompanyTags.none(), true, true);
        NewsArticle article = makeArticle("a1", "Tempus AI raises $100M");

        CompanyProfile result = service.upsertFromDiscovery(company, List.of(article), null);

        assertThat(result.slug()).isEqualTo("tempus-ai");
        assertThat(result.name()).isEqualTo("Tempus AI");
        assertThat(result.url()).isEqualTo("https://tempus.com");
        assertThat(result.description()).isEqualTo("Clinical data platform");
        assertThat(result.articleIds()).containsExactly("a1");
        assertThat(result.articleCount()).isEqualTo(1);
        assertThat(result.trendDirection()).isEqualTo(TrendDirection.NEW);
    }

    @Test
    void upsertMergesArticleIdsWithExisting() {
        Instant past = Instant.parse("2026-01-01T00:00:00Z");
        CompanyProfile existing = new CompanyProfile("tempus-ai", "Tempus AI",
                "https://tempus.com", "Old description", List.of("scribe"),
                List.of("a1"), past, past, 1, TrendDirection.STABLE);

        Company company = new Company("Tempus AI", "YC", "https://yc.com/tempus",
                null, "New description", CompanyTags.none(), true, true);
        NewsArticle article2 = makeArticle("a2", "Tempus FDA clearance");

        CompanyProfile result = service.upsertFromDiscovery(company, List.of(article2), existing);

        assertThat(result.slug()).isEqualTo("tempus-ai");
        assertThat(result.articleIds()).containsExactly("a1", "a2");
        assertThat(result.articleCount()).isEqualTo(2);
        assertThat(result.description()).isEqualTo("New description");
        assertThat(result.firstDiscoveredAt()).isEqualTo(past);
    }

    @Test
    void upsertSkipsDuplicateArticleIds() {
        Instant past = Instant.parse("2026-01-01T00:00:00Z");
        CompanyProfile existing = new CompanyProfile("tempus-ai", "Tempus AI",
                "https://tempus.com", "Description", List.of(), List.of("a1"),
                past, past, 1, TrendDirection.STABLE);

        Company company = new Company("Tempus AI", "YC", "https://yc.com/tempus",
                null, "", CompanyTags.none(), true, true);
        NewsArticle article = makeArticle("a1", "Duplicate article");

        CompanyProfile result = service.upsertFromDiscovery(company, List.of(article), existing);

        assertThat(result.articleIds()).containsExactly("a1");
        assertThat(result.articleCount()).isEqualTo(1);
    }

    @Test
    void upsertFallsBackToExistingDescriptionWhenNewIsBlank() {
        Instant past = Instant.parse("2026-01-01T00:00:00Z");
        CompanyProfile existing = new CompanyProfile("acme", "Acme",
                "https://acme.com", "Original description", List.of(), List.of(),
                past, past, 0, TrendDirection.STABLE);

        Company company = new Company("Acme", "YC", "https://yc.com/acme",
                null, "", CompanyTags.none(), true, true);

        CompanyProfile result = service.upsertFromDiscovery(company, List.of(), existing);

        assertThat(result.description()).isEqualTo("Original description");
    }

    @Test
    void upsertBuildsCategoriesFromTags() {
        Company company = new Company("ImgCo", "TopStartups", "https://example.com",
                "https://imgco.com", "Imaging AI",
                new CompanyTags(false, false, true, false, false), true, true);

        CompanyProfile result = service.upsertFromDiscovery(company, List.of(), null);

        assertThat(result.categories()).containsExactly("imaging");
    }

    // --- detectEvents tests ---

    @Test
    void detectEventsFundingKeyword() {
        CompanyProfile profile = makeProfile("tempus-ai", "Tempus AI");
        NewsArticle article = makeArticle("a1", "Tempus AI raises $200M in Series D");

        List<CompanyEvent> events = service.detectEvents(profile, List.of(article));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(CompanyEventType.FUNDING);
        assertThat(events.get(0).companySlug()).isEqualTo("tempus-ai");
    }

    @Test
    void detectEventsRegulatoryKeyword() {
        CompanyProfile profile = makeProfile("aidoc", "Aidoc");
        NewsArticle article = makeArticle("a2", "Aidoc receives FDA clearance for chest CT");

        List<CompanyEvent> events = service.detectEvents(profile, List.of(article));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(CompanyEventType.REGULATORY);
    }

    @Test
    void detectEventsProductLaunchKeyword() {
        CompanyProfile profile = makeProfile("ambience", "Ambience");
        NewsArticle article = makeArticle("a3", "Ambience launches new AI scribe platform");

        List<CompanyEvent> events = service.detectEvents(profile, List.of(article));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(CompanyEventType.PRODUCT_LAUNCH);
    }

    @Test
    void detectEventsPartnershipKeyword() {
        CompanyProfile profile = makeProfile("nuance", "Nuance");
        NewsArticle article = makeArticle("a4", "Nuance partners with Epic for AI integration");

        List<CompanyEvent> events = service.detectEvents(profile, List.of(article));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(CompanyEventType.PARTNERSHIP);
    }

    @Test
    void detectEventsReturnsEmptyWhenNoKeywordsMatch() {
        CompanyProfile profile = makeProfile("generic", "Generic Co");
        NewsArticle article = makeArticle("a5", "Generic Co reports quarterly earnings");

        List<CompanyEvent> events = service.detectEvents(profile, List.of(article));

        assertThat(events).isEmpty();
    }

    // --- getTopCompanies tests ---

    @Test
    void getTopCompaniesReturnsSortedByArticleCount() {
        CompanyProfile p1 = makeProfileWithCount("a", "A Co", 5);
        CompanyProfile p2 = makeProfileWithCount("b", "B Co", 20);
        CompanyProfile p3 = makeProfileWithCount("c", "C Co", 10);

        List<CompanyProfile> result = service.getTopCompanies(List.of(p1, p2, p3), 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).slug()).isEqualTo("b");
        assertThat(result.get(1).slug()).isEqualTo("c");
    }

    @Test
    void getTopCompaniesReturnsAllWhenLimitExceedsSize() {
        CompanyProfile p1 = makeProfileWithCount("a", "A Co", 5);

        List<CompanyProfile> result = service.getTopCompanies(List.of(p1), 10);

        assertThat(result).hasSize(1);
    }

    // --- toSlug tests ---

    @Test
    void toSlugConvertsNameToKebabCase() {
        assertThat(service.toSlug("Tempus AI")).isEqualTo("tempus-ai");
        assertThat(service.toSlug("Aidoc Medical")).isEqualTo("aidoc-medical");
        assertThat(service.toSlug("Google DeepMind Health")).isEqualTo("google-deepmind-health");
    }

    @Test
    void toSlugStripsSpecialCharacters() {
        assertThat(service.toSlug("Acme, Inc.")).isEqualTo("acme-inc");
        assertThat(service.toSlug("Health+AI")).isEqualTo("healthai");
    }

    // --- helpers ---

    private NewsArticle makeArticle(String id, String title) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                null, "Topic", null, null, "Source", "INDUSTRY", 0.5, Instant.now());
    }

    private CompanyProfile makeProfile(String slug, String name) {
        Instant now = Instant.now();
        return new CompanyProfile(slug, name, "https://example.com", "Description",
                List.of(), List.of(), now, now, 0, TrendDirection.NEW);
    }

    private CompanyProfile makeProfileWithCount(String slug, String name, int count) {
        Instant now = Instant.now();
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(slug + "-" + i);
        }
        return new CompanyProfile(slug, name, "https://example.com", "Description",
                List.of(), ids, now, now, count, TrendDirection.STABLE);
    }
}
