package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WatchlistMatchingService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class WatchlistMatchingServiceTest {

    private WatchlistMatchingService service;

    @BeforeEach
    void setUp() {
        service = new WatchlistMatchingService();
    }

    @Test
    void keywordMatchInTitle() {
        NewsArticle article = article("a1", "FDA clears new AI diagnostic tool", null);
        WatchlistItem item = keywordItem("w1", "user@test.com", "FDA");

        List<WatchlistMatch> matches = service.matchArticlesAgainstWatchlist(
                List.of(article), List.of(item));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).itemId()).isEqualTo("w1");
        assertThat(matches.get(0).articleId()).isEqualTo("a1");
    }

    @Test
    void keywordMatchInBody() {
        NewsArticle article = article("a1", "Healthcare news", "The FDA announced new guidance");
        WatchlistItem item = keywordItem("w1", "user@test.com", "FDA");

        List<WatchlistMatch> matches = service.matchArticlesAgainstWatchlist(
                List.of(article), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void keywordMatchIsCaseInsensitive() {
        NewsArticle article = article("a1", "fda clearance granted", null);
        WatchlistItem item = keywordItem("w1", "user@test.com", "FDA");

        List<WatchlistMatch> matches = service.matchArticlesAgainstWatchlist(
                List.of(article), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void companyMatchUsesLabel() {
        NewsArticle article = article("a1", "Tempus AI raises $200M in Series D", null);
        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.COMPANY, "tempus-ai", "Tempus AI", Instant.now());

        List<WatchlistMatch> matches = service.matchArticlesAgainstWatchlist(
                List.of(article), List.of(item));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).itemId()).isEqualTo("w1");
    }

    @Test
    void topicMatchUsesArticleTopic() {
        NewsArticle article = new NewsArticle("a1", "Some article",
                URI.create("https://example.com"), "body",
                "AI Healthcare Government Policy",
                null, null, "PubMed", "ACADEMIC", 0.9, Instant.now());
        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.TOPIC, "AI Healthcare Government Policy",
                "Gov Policy", Instant.now());

        List<WatchlistMatch> matches = service.matchArticlesAgainstWatchlist(
                List.of(article), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void noMatchReturnsEmpty() {
        NewsArticle article = article("a1", "Weather forecast for tomorrow", null);
        WatchlistItem item = keywordItem("w1", "user@test.com", "FDA");

        List<WatchlistMatch> matches = service.matchArticlesAgainstWatchlist(
                List.of(article), List.of(item));

        assertThat(matches).isEmpty();
    }

    @Test
    void duplicateWithinBatchIsSuppressed() {
        NewsArticle article = article("a1", "FDA clears new tool", "FDA guidance issued");
        WatchlistItem item = keywordItem("w1", "user@test.com", "FDA");

        List<WatchlistMatch> matches = service.matchArticlesAgainstWatchlist(
                List.of(article, article), List.of(item));

        // Same article+item should only produce one match
        assertThat(matches).hasSize(1);
    }

    @Test
    void snippetIsExtracted() {
        NewsArticle article = article("a1", "Big news: FDA clears diagnostic device", null);
        WatchlistItem item = keywordItem("w1", "user@test.com", "FDA");

        List<WatchlistMatch> matches = service.matchArticlesAgainstWatchlist(
                List.of(article), List.of(item));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).snippet()).isNotNull();
        assertThat(matches.get(0).snippet().toLowerCase()).contains("fda");
    }

    @Test
    void emptyArticlesReturnsEmpty() {
        WatchlistItem item = keywordItem("w1", "user@test.com", "FDA");

        List<WatchlistMatch> matches = service.matchArticlesAgainstWatchlist(
                List.of(), List.of(item));

        assertThat(matches).isEmpty();
    }

    @Test
    void emptyItemsReturnsEmpty() {
        NewsArticle article = article("a1", "FDA clears tool", null);

        List<WatchlistMatch> matches = service.matchArticlesAgainstWatchlist(
                List.of(article), List.of());

        assertThat(matches).isEmpty();
    }

    @Test
    void multipleItemsMatchSameArticle() {
        NewsArticle article = article("a1", "FDA clears Tempus AI diagnostic", null);
        WatchlistItem fdaItem = keywordItem("w1", "user@test.com", "FDA");
        WatchlistItem companyItem = new WatchlistItem("w2", "user@test.com",
                WatchlistItemType.COMPANY, "tempus-ai", "Tempus AI", Instant.now());

        List<WatchlistMatch> matches = service.matchArticlesAgainstWatchlist(
                List.of(article), List.of(fdaItem, companyItem));

        assertThat(matches).hasSize(2);
    }

    // --- Helpers ---

    private NewsArticle article(String id, String title, String body) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                body, "General AI Healthcare News", null, null, "TestSource",
                "INDUSTRY", 0.5, Instant.now());
    }

    private WatchlistItem keywordItem(String id, String email, String keyword) {
        return new WatchlistItem(id, email, WatchlistItemType.KEYWORD,
                keyword, keyword, Instant.now());
    }
}
