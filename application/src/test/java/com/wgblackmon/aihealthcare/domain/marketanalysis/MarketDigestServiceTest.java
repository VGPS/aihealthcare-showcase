package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.DealTermsPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestNotifier;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.PrivateFundingPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.RegulatoryTrackerRepository;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.SecondaryNewsCheckPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MarketDigestService} — qualifying-bar filter and sort-by-rank logic.
 *
 * <p>Ports are hand-rolled stubs (no-op implementations) — no real adapters exist yet.
 * The full pipeline (generateDailyDigest) is not tested here; it is covered in Slice 1.6.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-09-07
 */
class MarketDigestServiceTest {

    private MarketDigestService service;

    @BeforeEach
    void setUp() {
        service = new MarketDigestService(
                mock(MarketNewsResearchPort.class),
                mock(MarketDataPort.class),
                mock(ImpactClassifierPort.class),
                mock(MarketDigestRepository.class),
                mock(MarketDigestNotifier.class),
                null, 0.93, null, null,
                null, null, null
        );
    }

    // --- isMarketMoving: always-qualifying categories ---

    @Test
    void earnings_qualifies() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.EARNINGS, null, 1))).isTrue();
    }

    @Test
    void regulatory_qualifies() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.REGULATORY, null, 1))).isTrue();
    }

    @Test
    void mAndA_qualifies() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.M_AND_A, null, 1))).isTrue();
    }

    @Test
    void majorPartnership_qualifies() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.MAJOR_PARTNERSHIP, null, 1))).isTrue();
    }

    @Test
    void other_doesNotQualify() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.OTHER, null, 1))).isFalse();
    }

    // --- isMarketMoving: FUNDING boundary cases ---

    @Test
    void funding_aboveThreshold_qualifies() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.FUNDING, 50_000_001L, 1))).isTrue();
    }

    @Test
    void funding_atExactThreshold_doesNotQualify() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.FUNDING, 50_000_000L, 1))).isFalse();
    }

    @Test
    void funding_belowThreshold_doesNotQualify() {
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.FUNDING, 49_999_999L, 1))).isFalse();
    }

    @Test
    void funding_nullDealSize_doesNotQualify() {
        // null dealSizeUsd → dealSizeUsd() returns 0L → does not qualify
        assertThat(service.isMarketMoving(makeEntry(NewsCategory.FUNDING, null, 1))).isFalse();
    }

    // --- filterAndSort ---

    @Test
    void filterAndSort_onlyReturnsQualifyingEntries() {
        List<MarketDigestEntry> entries = List.of(
                makeEntry(NewsCategory.EARNINGS, null, 2),
                makeEntry(NewsCategory.OTHER, null, 1),
                makeEntry(NewsCategory.REGULATORY, null, 3),
                makeEntry(NewsCategory.FUNDING, 40_000_000L, 1)  // below threshold
        );
        List<MarketDigestEntry> result = service.filterAndSort(entries);
        assertThat(result).hasSize(2);
        for (MarketDigestEntry e : result) {
            assertThat(e.category()).isIn(NewsCategory.EARNINGS, NewsCategory.REGULATORY);
        }
    }

    @Test
    void filterAndSort_sortsByRankAscending() {
        List<MarketDigestEntry> entries = List.of(
                makeEntry(NewsCategory.EARNINGS, null, 3),
                makeEntry(NewsCategory.REGULATORY, null, 1),
                makeEntry(NewsCategory.M_AND_A, null, 2)
        );
        List<MarketDigestEntry> result = service.filterAndSort(entries);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).rank().value()).isEqualTo(1);
        assertThat(result.get(1).rank().value()).isEqualTo(2);
        assertThat(result.get(2).rank().value()).isEqualTo(3);
    }

    @Test
    void filterAndSort_emptyInput_returnsEmpty() {
        assertThat(service.filterAndSort(List.of())).isEmpty();
    }

    // --- mergeWithSecondaryCheck ---

    @Test
    void merge_nullSecondaryPort_returnsPrimary() {
        MarketNewsItem item = makeItem("Primary Headline", NewsCategory.EARNINGS);
        List<MarketNewsItem> result = service.mergeWithSecondaryCheck(
                List.of(item), Instant.now(), Instant.now());
        assertThat(result).containsExactly(item);
    }

    @Test
    void merge_secondaryReturnsNovelItem_appendsIt() {
        SecondaryNewsCheckPort port = mock(SecondaryNewsCheckPort.class);
        MarketDigestService svc = new MarketDigestService(
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort.class),
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort.class),
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort.class),
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository.class),
                null, null, 0.93, port, null,
                null, null, null);

        MarketNewsItem primary = makeItem("Primary Headline", NewsCategory.EARNINGS);
        MarketNewsItem secondary = makeItem("Novel Secondary Headline", NewsCategory.REGULATORY);
        when(port.findRecentNews(any(), any())).thenReturn(List.of(secondary));

        List<MarketNewsItem> result = svc.mergeWithSecondaryCheck(
                List.of(primary), Instant.now(), Instant.now());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).headline()).isEqualTo("Primary Headline");
        assertThat(result.get(1).headline()).isEqualTo("Novel Secondary Headline");
    }

    @Test
    void merge_secondaryDuplicatesHeadline_notAppended() {
        SecondaryNewsCheckPort port = mock(SecondaryNewsCheckPort.class);
        MarketDigestService svc = new MarketDigestService(
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort.class),
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort.class),
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort.class),
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository.class),
                null, null, 0.93, port, null,
                null, null, null);

        MarketNewsItem primary = makeItem("Doximity Q2 Earnings Beat Estimates!", NewsCategory.EARNINGS);
        MarketNewsItem duplicate = makeItem("doximity q2 earnings beat estimates!", NewsCategory.EARNINGS);
        when(port.findRecentNews(any(), any())).thenReturn(List.of(duplicate));

        List<MarketNewsItem> result = svc.mergeWithSecondaryCheck(
                List.of(primary), Instant.now(), Instant.now());

        assertThat(result).hasSize(1);
    }

    @Test
    void merge_secondaryThrows_returnsPrimaryOnly() {
        SecondaryNewsCheckPort port = mock(SecondaryNewsCheckPort.class);
        MarketDigestService svc = new MarketDigestService(
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort.class),
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort.class),
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort.class),
                mock(com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository.class),
                null, null, 0.93, port, null,
                null, null, null);

        MarketNewsItem primary = makeItem("Primary Headline", NewsCategory.M_AND_A);
        when(port.findRecentNews(any(), any())).thenThrow(new RuntimeException("API error"));

        List<MarketNewsItem> result = svc.mergeWithSecondaryCheck(
                List.of(primary), Instant.now(), Instant.now());

        assertThat(result).containsExactly(primary);
    }

    @Test
    void normalizeHeadline_stripsNonAlphanumericAndLowercases() {
        assertThat(MarketDigestService.normalizeHeadline("Doximity Q2 Earnings — Beat Estimates!"))
                .isEqualTo("doximityq2earningsbeatestimates");
        // simpler case
        assertThat(MarketDigestService.normalizeHeadline("Hello, World!"))
                .isEqualTo("helloworld");
    }

    // --- inferJurisdiction ---

    @Test
    void inferJurisdiction_fda() {
        assertThat(MarketDigestService.inferJurisdiction("FDA clears new AI device")).isEqualTo(Jurisdiction.US_FDA);
    }

    @Test
    void inferJurisdiction_euAiAct() {
        assertThat(MarketDigestService.inferJurisdiction("EU AI Act deadline approaching")).isEqualTo(Jurisdiction.EU_AI_ACT);
    }

    @Test
    void inferJurisdiction_mhra() {
        assertThat(MarketDigestService.inferJurisdiction("UK MHRA issues guidance")).isEqualTo(Jurisdiction.UK_MHRA);
    }

    @Test
    void inferJurisdiction_stateLaw() {
        assertThat(MarketDigestService.inferJurisdiction("state law banning AI in diagnostics")).isEqualTo(Jurisdiction.US_STATE);
    }

    @Test
    void inferJurisdiction_unknown() {
        assertThat(MarketDigestService.inferJurisdiction("something happened")).isEqualTo(Jurisdiction.OTHER);
    }

    // --- inferRulemakingStage ---

    @Test
    void inferStage_enforcement() {
        assertThat(MarketDigestService.inferRulemakingStage("penalties enforced")).isEqualTo(RulemakingStage.ENFORCEMENT);
    }

    @Test
    void inferStage_finalGuidance() {
        assertThat(MarketDigestService.inferRulemakingStage("FDA approved new clearance")).isEqualTo(RulemakingStage.FINAL_GUIDANCE);
    }

    @Test
    void inferStage_commentPeriod() {
        assertThat(MarketDigestService.inferRulemakingStage("public comment period opens")).isEqualTo(RulemakingStage.COMMENT_PERIOD);
    }

    @Test
    void inferStage_draftGuidance() {
        assertThat(MarketDigestService.inferRulemakingStage("draft guidance released")).isEqualTo(RulemakingStage.DRAFT_GUIDANCE);
    }

    // --- extractDocketId ---

    @Test
    void extractDocketId_findsPattern() {
        assertThat(MarketDigestService.extractDocketId("FDA-2024-N-2177 finalizes rule", "headline"))
                .isEqualTo("FDA-2024-N-2177");
    }

    @Test
    void extractDocketId_fallsBackToHash() {
        String result = MarketDigestService.extractDocketId("no docket here", "headline");
        assertThat(result).startsWith("REG-");
    }

    // --- inferFundingStage ---

    @Test
    void inferFundingStage_seriesA() {
        assertThat(MarketDigestService.inferFundingStage("raises $50M in Series A round")).isEqualTo("Series A");
    }

    @Test
    void inferFundingStage_seed() {
        assertThat(MarketDigestService.inferFundingStage("seed round completed")).isEqualTo("Seed");
    }

    @Test
    void inferFundingStage_undisclosed() {
        assertThat(MarketDigestService.inferFundingStage("new funding round")).isEqualTo("Undisclosed");
    }

    // --- extractRegulatoryTrackers ---

    @Test
    void extractRegulatoryTrackers_nullPort_noOp() {
        service.extractRegulatoryTrackers(List.of(
                makeEntry(NewsCategory.REGULATORY, null, 1)));
    }

    @Test
    void extractRegulatoryTrackers_upsertsForRegulatoryEntries() {
        RegulatoryTrackerRepository trackerRepo = mock(RegulatoryTrackerRepository.class);
        MarketDigestService svc = new MarketDigestService(
                mock(MarketNewsResearchPort.class), mock(MarketDataPort.class),
                mock(ImpactClassifierPort.class), mock(MarketDigestRepository.class),
                null, null, 0.93, null, null,
                trackerRepo, null, null);

        svc.extractRegulatoryTrackers(List.of(
                makeEntryWithHeadline("FDA clears new AI diagnostic", NewsCategory.REGULATORY),
                makeEntryWithHeadline("Doximity beats estimates", NewsCategory.EARNINGS)));

        org.mockito.Mockito.verify(trackerRepo, org.mockito.Mockito.times(1))
                .upsert(any(RegulatoryTracker.class));
    }

    // --- extractPrivateFundingRounds ---

    @Test
    void extractPrivateFundingRounds_savesForPrivateCompanies() {
        PrivateFundingPort fundingPort = mock(PrivateFundingPort.class);
        MarketDigestService svc = new MarketDigestService(
                mock(MarketNewsResearchPort.class), mock(MarketDataPort.class),
                mock(ImpactClassifierPort.class), mock(MarketDigestRepository.class),
                null, null, 0.93, null, null,
                null, fundingPort, null);

        AffectedCompany privateCompany = new AffectedCompany("Abridge", null, "subject", PeerGroup.AI_SCRIBE_DOCUMENTATION);
        MarketNewsItem item = new MarketNewsItem("Abridge raises $150M Series B",
                "Summary.", List.of("https://example.com"), Instant.now(), NewsCategory.FUNDING, 150_000_000L);
        MarketDigestEntry entry = new MarketDigestEntry(item, List.of(),
                FactClassification.CONFIRMED, new MarketImpactRank(2), List.of(privateCompany));

        svc.extractPrivateFundingRounds(List.of(entry));

        org.mockito.Mockito.verify(fundingPort, org.mockito.Mockito.times(1))
                .save(any(PrivateFundingRound.class), any(PeerGroup.class));
    }

    @Test
    void extractPrivateFundingRounds_skipsPublicCompanies() {
        PrivateFundingPort fundingPort = mock(PrivateFundingPort.class);
        MarketDigestService svc = new MarketDigestService(
                mock(MarketNewsResearchPort.class), mock(MarketDataPort.class),
                mock(ImpactClassifierPort.class), mock(MarketDigestRepository.class),
                null, null, 0.93, null, null,
                null, fundingPort, null);

        AffectedCompany publicCompany = new AffectedCompany("Doximity", "DOCS", "subject", null);
        MarketDigestEntry entry = new MarketDigestEntry(
                makeItem("Doximity raises capital", NewsCategory.FUNDING),
                List.of(), FactClassification.CONFIRMED, new MarketImpactRank(2), List.of(publicCompany));

        svc.extractPrivateFundingRounds(List.of(entry));

        org.mockito.Mockito.verify(fundingPort, org.mockito.Mockito.never())
                .save(any(PrivateFundingRound.class), any(PeerGroup.class));
    }

    // --- extractDealTerms ---

    @Test
    void extractDealTerms_savesForMAndA() {
        DealTermsPort termsPort = mock(DealTermsPort.class);
        MarketDigestService svc = new MarketDigestService(
                mock(MarketNewsResearchPort.class), mock(MarketDataPort.class),
                mock(ImpactClassifierPort.class), mock(MarketDigestRepository.class),
                null, null, 0.93, null, null,
                null, null, termsPort);

        MarketDigestEntry entry = new MarketDigestEntry(
                makeItem("Big acquisition completed", NewsCategory.M_AND_A),
                List.of(), FactClassification.CONFIRMED, new MarketImpactRank(1), List.of());

        svc.extractDealTerms(List.of(entry));

        org.mockito.Mockito.verify(termsPort, org.mockito.Mockito.times(1))
                .save(any(String.class), any(DealTerms.class));
    }

    @Test
    void extractDealTerms_skipsNonMAndA() {
        DealTermsPort termsPort = mock(DealTermsPort.class);
        MarketDigestService svc = new MarketDigestService(
                mock(MarketNewsResearchPort.class), mock(MarketDataPort.class),
                mock(ImpactClassifierPort.class), mock(MarketDigestRepository.class),
                null, null, 0.93, null, null,
                null, null, termsPort);

        svc.extractDealTerms(List.of(makeEntry(NewsCategory.EARNINGS, null, 1)));

        org.mockito.Mockito.verify(termsPort, org.mockito.Mockito.never())
                .save(any(String.class), any(DealTerms.class));
    }

    // --- helper ---

    private static MarketDigestEntry makeEntryWithHeadline(String headline, NewsCategory category) {
        MarketNewsItem item = new MarketNewsItem(headline, "Summary.",
                List.of("https://example.com"), Instant.now(), category, null);
        return new MarketDigestEntry(item, List.of(), FactClassification.CONFIRMED,
                new MarketImpactRank(1), List.of());
    }

    private static MarketNewsItem makeItem(String headline, NewsCategory category) {
        return new MarketNewsItem(
                headline, "Summary.", List.of("https://example.com"),
                Instant.now(), category, null);
    }

    private static MarketDigestEntry makeEntry(NewsCategory category, Long dealSizeUsd, int rank) {
        MarketNewsItem item = new MarketNewsItem(
                "Headline",
                "Summary.",
                List.of("https://example.com"),
                Instant.now(),
                category,
                dealSizeUsd
        );
        return new MarketDigestEntry(
                item,
                List.of(),
                FactClassification.CONFIRMED,
                new MarketImpactRank(rank),
                List.of()
        );
    }
}
