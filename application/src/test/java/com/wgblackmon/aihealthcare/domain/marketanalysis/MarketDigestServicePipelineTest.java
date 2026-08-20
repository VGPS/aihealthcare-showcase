package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.EntryEmbeddingPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestNotifier;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MarketDigestService#generateDailyDigest(LocalDate)}.
 *
 * <p>Covers the full pipeline: idempotency, empty-result shortcut,
 * qualifier filtering, persistence, and notification triggering.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@ExtendWith(MockitoExtension.class)
class MarketDigestServicePipelineTest {

    @Mock private MarketNewsResearchPort newsResearch;
    @Mock private MarketDataPort         marketData;
    @Mock private ImpactClassifierPort   impactClassifier;
    @Mock private MarketDigestRepository repository;
    @Mock private MarketDigestNotifier   notifier;
    @Mock private EntryEmbeddingPort     embeddingPort;

    private MarketDigestService service;

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @BeforeEach
    void setUp() {
        service = new MarketDigestService(newsResearch, marketData, impactClassifier,
                repository, notifier, embeddingPort, 0.93, null, null);
        when(repository.findByDate(TODAY)).thenReturn(Optional.empty());
    }

    // ─── idempotency ─────────────────────────────────────────────────────────

    @Test
    void generateDailyDigest_whenDigestAlreadyExists_skipsResearchAndReturnsCached() {
        MarketDigest existing = MarketDigest.empty(TODAY);
        when(repository.findByDate(TODAY)).thenReturn(Optional.of(existing));

        MarketDigest result = service.generateDailyDigest(TODAY);

        assertThat(result).isSameAs(existing);
        verify(newsResearch, never()).findRecentAiHealthcareNews(any());
        verify(impactClassifier, never()).classify(any());
        verify(repository, never()).save(any());
    }

    // ─── empty news shortcut ─────────────────────────────────────────────────

    @Test
    void generateDailyDigest_whenNoNewsFound_savesEmptyDigestAndSkipsClassifier() {
        when(newsResearch.findRecentAiHealthcareNews(any())).thenReturn(List.of());

        MarketDigest result = service.generateDailyDigest(TODAY);

        assertThat(result.entries()).isEmpty();
        assertThat(result.date()).isEqualTo(TODAY);

        ArgumentCaptor<MarketDigest> saved = ArgumentCaptor.forClass(MarketDigest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().entries()).isEmpty();

        verify(impactClassifier, never()).classify(any());
        verify(notifier, never()).notify(any());
    }

    // ─── qualifying bar applied ───────────────────────────────────────────────

    @Test
    void generateDailyDigest_filtersNonQualifyingEntries() {
        MarketNewsItem qualifying = newsItem(NewsCategory.M_AND_A, null);
        MarketNewsItem nonQualifying = newsItem(NewsCategory.OTHER, null);

        when(newsResearch.findRecentAiHealthcareNews(any()))
                .thenReturn(List.of(qualifying, nonQualifying));

        MarketDigestEntry qualifiedEntry = classifiedEntry(qualifying, NewsCategory.M_AND_A, 2);
        MarketDigestEntry nonQualEntry   = classifiedEntry(nonQualifying, NewsCategory.OTHER, 4);

        when(impactClassifier.classify(any())).thenReturn(List.of(qualifiedEntry, nonQualEntry));

        MarketDigest result = service.generateDailyDigest(TODAY);

        assertThat(result.entries()).hasSize(1);
        assertThat(result.entries().get(0).newsItem().headline()).isEqualTo("M_AND_A headline");
    }

    // ─── persistence ─────────────────────────────────────────────────────────

    @Test
    void generateDailyDigest_savesDigestWithCorrectDate() {
        MarketNewsItem item = newsItem(NewsCategory.EARNINGS, null);
        when(newsResearch.findRecentAiHealthcareNews(any())).thenReturn(List.of(item));
        when(impactClassifier.classify(any()))
                .thenReturn(List.of(classifiedEntry(item, NewsCategory.EARNINGS, 1)));

        service.generateDailyDigest(TODAY);

        ArgumentCaptor<MarketDigest> saved = ArgumentCaptor.forClass(MarketDigest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().date()).isEqualTo(TODAY);
    }

    // ─── notifier triggered only when entries qualify ────────────────────────

    @Test
    void generateDailyDigest_withQualifyingEntries_invokesNotifier() {
        MarketNewsItem item = newsItem(NewsCategory.REGULATORY, null);
        when(newsResearch.findRecentAiHealthcareNews(any())).thenReturn(List.of(item));
        when(impactClassifier.classify(any()))
                .thenReturn(List.of(classifiedEntry(item, NewsCategory.REGULATORY, 2)));

        service.generateDailyDigest(TODAY);

        verify(notifier).notify(any(MarketDigest.class));
    }

    @Test
    void generateDailyDigest_withOnlyNonQualifyingEntries_doesNotInvokeNotifier() {
        MarketNewsItem item = newsItem(NewsCategory.OTHER, null);
        when(newsResearch.findRecentAiHealthcareNews(any())).thenReturn(List.of(item));
        when(impactClassifier.classify(any()))
                .thenReturn(List.of(classifiedEntry(item, NewsCategory.OTHER, 5)));

        service.generateDailyDigest(TODAY);

        verify(notifier, never()).notify(any());
    }

    // ─── null notifier ────────────────────────────────────────────────────────

    @Test
    void generateDailyDigest_withNullNotifier_doesNotThrow() {
        MarketDigestService serviceNoNotifier = new MarketDigestService(
                newsResearch, marketData, impactClassifier, repository, null, null, 0.93, null, null);
        when(repository.findByDate(TODAY)).thenReturn(Optional.empty());

        MarketNewsItem item = newsItem(NewsCategory.EARNINGS, null);
        when(newsResearch.findRecentAiHealthcareNews(any())).thenReturn(List.of(item));
        when(impactClassifier.classify(any()))
                .thenReturn(List.of(classifiedEntry(item, NewsCategory.EARNINGS, 1)));

        MarketDigest result = serviceNoNotifier.generateDailyDigest(TODAY);

        assertThat(result.entries()).hasSize(1);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private MarketNewsItem newsItem(NewsCategory category, Long dealSize) {
        return new MarketNewsItem(
                category.name() + " headline",
                "Summary.",
                List.of(),
                Instant.now(),
                category,
                dealSize
        );
    }

    private MarketDigestEntry classifiedEntry(MarketNewsItem item, NewsCategory category, int rank) {
        return new MarketDigestEntry(
                item,
                List.of(),
                FactClassification.CONFIRMED,
                new MarketImpactRank(rank),
                List.of()
        );
    }
}
