package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RegulatoryWatchlistMatcher}.
 *
 * <p>Verifies keyword, company, and topic matching against regulatory events,
 * following the same test patterns as {@link WatchlistMatchingServiceTest}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class RegulatoryWatchlistMatcherTest {

    private RegulatoryWatchlistMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new RegulatoryWatchlistMatcher();
    }

    @Test
    void keywordMatchInTitle() {
        RegulatoryEvent event = event("e1", "FDA clears AI radiology tool", null, null, null);
        WatchlistItem item = keywordItem("w1", "radiology");

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(event), List.of(item));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).itemId()).isEqualTo("w1");
        assertThat(matches.get(0).articleId()).isEqualTo("e1");
    }

    @Test
    void keywordMatchInSummary() {
        RegulatoryEvent event = event("e1", "510(k) Clearance", "AI-powered radiology device", null, null);
        WatchlistItem item = keywordItem("w1", "radiology");

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(event), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void keywordMatchInDeviceName() {
        RegulatoryEvent event = event("e1", "510(k) Clearance", null, null, "AI Radiology Scanner");
        WatchlistItem item = keywordItem("w1", "radiology");

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(event), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void companyMatchInApplicantName() {
        RegulatoryEvent event = event("e1", "510(k) Clearance", null, "Tempus AI Inc", null);
        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.COMPANY, "tempus-ai", "Tempus AI", Instant.now());

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(event), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void companyMatchIsCaseInsensitive() {
        RegulatoryEvent event = event("e1", "510(k) Clearance", null, "TEMPUS AI INC", null);
        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.COMPANY, "tempus-ai", "Tempus AI", Instant.now());

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(event), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void topicMatchesAgainstKeywords() {
        RegulatoryEvent event = new RegulatoryEvent("e1", RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "Clearance", null, null, null, null,
                "https://fda.gov/e1", null, null, Instant.now(),
                List.of("radiology", "machine learning"),
                null, null, null, null);
        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.TOPIC, "radiology", "Radiology", Instant.now());

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(event), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void noMatchReturnsEmpty() {
        RegulatoryEvent event = event("e1", "CMS rule on billing codes", null, null, null);
        WatchlistItem item = keywordItem("w1", "radiology");

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(event), List.of(item));

        assertThat(matches).isEmpty();
    }

    @Test
    void duplicateWithinBatchIsSuppressed() {
        RegulatoryEvent event = event("e1", "FDA clears radiology tool", null, null, null);
        WatchlistItem item = keywordItem("w1", "radiology");

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(event, event), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void emptyEventsReturnsEmpty() {
        WatchlistItem item = keywordItem("w1", "FDA");

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(), List.of(item));

        assertThat(matches).isEmpty();
    }

    @Test
    void emptyItemsReturnsEmpty() {
        RegulatoryEvent event = event("e1", "FDA clears tool", null, null, null);

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(event), List.of());

        assertThat(matches).isEmpty();
    }

    @Test
    void multipleItemsMatchSameEvent() {
        RegulatoryEvent event = event("e1", "FDA clears Tempus AI diagnostic", null,
                "Tempus AI Inc", "AI Diagnostic Tool");
        WatchlistItem fdaItem = keywordItem("w1", "FDA");
        WatchlistItem companyItem = new WatchlistItem("w2", "user@test.com",
                WatchlistItemType.COMPANY, "tempus-ai", "Tempus AI", Instant.now());

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(event), List.of(fdaItem, companyItem));

        assertThat(matches).hasSize(2);
    }

    @Test
    void snippetIsExtracted() {
        RegulatoryEvent event = event("e1", "FDA clears AI radiology diagnostic device", null, null, null);
        WatchlistItem item = keywordItem("w1", "radiology");

        List<WatchlistMatch> matches = matcher.matchEventsAgainstWatchlist(
                List.of(event), List.of(item));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).snippet()).isNotNull();
        assertThat(matches.get(0).snippet().toLowerCase()).contains("radiology");
    }

    // --- Helpers ---

    private RegulatoryEvent event(String id, String title, String summary,
                                   String applicant, String device) {
        return new RegulatoryEvent(id, RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, title, summary, null, applicant, device,
                "https://fda.gov/" + id, null, null, Instant.now(), null,
                null, null, null, null);
    }

    private WatchlistItem keywordItem(String id, String keyword) {
        return new WatchlistItem(id, "user@test.com", WatchlistItemType.KEYWORD,
                keyword, keyword, Instant.now());
    }
}
