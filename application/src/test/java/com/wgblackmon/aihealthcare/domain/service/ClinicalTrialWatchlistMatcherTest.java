package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ClinicalTrialWatchlistMatcher}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
class ClinicalTrialWatchlistMatcherTest {

    private static final Instant NOW = Instant.now();
    private ClinicalTrialWatchlistMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new ClinicalTrialWatchlistMatcher();
    }

    @Test
    void keywordMatchesInTitle() {
        ClinicalTrial trial = trial("t1", "NCT001", "AI-Assisted Radiology Study", null, null);
        WatchlistItem item = keywordItem("w1", "radiology");

        List<WatchlistMatch> matches = matcher.matchTrialsAgainstWatchlist(List.of(trial), List.of(item));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).itemId()).isEqualTo("w1");
        assertThat(matches.get(0).articleId()).isEqualTo("t1");
    }

    @Test
    void keywordMatchesInSummary() {
        ClinicalTrial trial = trial("t1", "NCT001", "Generic Trial", null,
                "This trial uses deep learning for pathology diagnosis");
        WatchlistItem item = keywordItem("w1", "pathology");

        List<WatchlistMatch> matches = matcher.matchTrialsAgainstWatchlist(List.of(trial), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void keywordMatchesInConditions() {
        ClinicalTrial trial = trialWithConditions("t1", "NCT001", "Generic Trial",
                List.of("Lung Cancer", "NSCLC"));
        WatchlistItem item = keywordItem("w1", "lung cancer");

        List<WatchlistMatch> matches = matcher.matchTrialsAgainstWatchlist(List.of(trial), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void companyMatchesInSponsor() {
        ClinicalTrial trial = trial("t1", "NCT001", "AI Drug Trial", "Tempus AI", null);
        WatchlistItem item = companyItem("w1", "Tempus AI");

        List<WatchlistMatch> matches = matcher.matchTrialsAgainstWatchlist(List.of(trial), List.of(item));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).itemId()).isEqualTo("w1");
    }

    @Test
    void companyMatchIsCaseInsensitive() {
        ClinicalTrial trial = trial("t1", "NCT001", "AI Drug Trial", "TEMPUS AI INC", null);
        WatchlistItem item = companyItem("w1", "tempus ai");

        List<WatchlistMatch> matches = matcher.matchTrialsAgainstWatchlist(List.of(trial), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void topicMatchesInKeywords() {
        ClinicalTrial trial = trialWithKeywords("t1", "NCT001", "AI Study",
                List.of("deep learning", "radiology"));
        WatchlistItem item = topicItem("w1", "deep learning");

        List<WatchlistMatch> matches = matcher.matchTrialsAgainstWatchlist(List.of(trial), List.of(item));

        assertThat(matches).hasSize(1);
    }

    @Test
    void noMatchReturnsEmpty() {
        ClinicalTrial trial = trial("t1", "NCT001", "Cardiology Study", "HeartCo", null);
        WatchlistItem item = keywordItem("w1", "oncology");

        List<WatchlistMatch> matches = matcher.matchTrialsAgainstWatchlist(List.of(trial), List.of(item));

        assertThat(matches).isEmpty();
    }

    @Test
    void deduplicatesWithinBatch() {
        ClinicalTrial trial = trial("t1", "NCT001", "AI Radiology Study", null, null);
        WatchlistItem item = keywordItem("w1", "radiology");

        // Same trial + item pair should only produce one match
        List<WatchlistMatch> matches = matcher.matchTrialsAgainstWatchlist(
                List.of(trial, trial), List.of(item));

        // Second trial has same trialId so same dedup key — but list has two separate objects
        // The dedup key is item.itemId() + "|" + trial.trialId(), so duplicates are filtered
        assertThat(matches).hasSize(1);
    }

    @Test
    void multipleTrialsMultipleItems() {
        ClinicalTrial t1 = trial("t1", "NCT001", "AI Radiology Study", "Tempus AI", null);
        ClinicalTrial t2 = trial("t2", "NCT002", "Cardiology Drug Trial", "HeartCo", null);
        WatchlistItem kw = keywordItem("w1", "radiology");
        WatchlistItem co = companyItem("w2", "Tempus AI");

        List<WatchlistMatch> matches = matcher.matchTrialsAgainstWatchlist(
                List.of(t1, t2), List.of(kw, co));

        // t1 matches kw (radiology in title) and co (Tempus AI in sponsor)
        // t2 matches neither
        assertThat(matches).hasSize(2);
    }

    @Test
    void snippetContainsMatchContext() {
        ClinicalTrial trial = trial("t1", "NCT001", "AI-Powered Radiology Imaging Tool", null, null);
        WatchlistItem item = keywordItem("w1", "radiology");

        List<WatchlistMatch> matches = matcher.matchTrialsAgainstWatchlist(List.of(trial), List.of(item));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).snippet()).isNotNull();
        assertThat(matches.get(0).snippet().toLowerCase()).contains("radiology");
    }

    // --- Helpers ---

    private ClinicalTrial trial(String trialId, String nctId, String title,
                                 String sponsor, String summary) {
        return new ClinicalTrial(trialId, nctId, title,
                sponsor, ClinicalTrialStatus.RECRUITING, null, null, summary,
                "https://clinicaltrials.gov/study/" + nctId, null, null, NOW, null);
    }

    private ClinicalTrial trialWithConditions(String trialId, String nctId, String title,
                                               List<String> conditions) {
        return new ClinicalTrial(trialId, nctId, title,
                null, ClinicalTrialStatus.RECRUITING, null, conditions, null,
                "https://clinicaltrials.gov/study/" + nctId, null, null, NOW, null);
    }

    private ClinicalTrial trialWithKeywords(String trialId, String nctId, String title,
                                             List<String> keywords) {
        return new ClinicalTrial(trialId, nctId, title,
                null, ClinicalTrialStatus.RECRUITING, null, null, null,
                "https://clinicaltrials.gov/study/" + nctId, null, null, NOW, keywords);
    }

    private WatchlistItem keywordItem(String itemId, String value) {
        return new WatchlistItem(itemId, "user@test.com", WatchlistItemType.KEYWORD,
                value, value, NOW);
    }

    private WatchlistItem companyItem(String itemId, String label) {
        return new WatchlistItem(itemId, "user@test.com", WatchlistItemType.COMPANY,
                label.toLowerCase().replace(" ", "-"), label, NOW);
    }

    private WatchlistItem topicItem(String itemId, String value) {
        return new WatchlistItem(itemId, "user@test.com", WatchlistItemType.TOPIC,
                value, value, NOW);
    }
}
