package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.EntryEmbeddingPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestNotifier;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the 7-day rolling dedup step in {@link MarketDigestService}.
 *
 * <p>Covers {@code deduplicateForNotification()} and {@code cosineSimilarity()} directly
 * to keep the pipeline test focused on integration behaviour.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@ExtendWith(MockitoExtension.class)
class MarketDigestServiceDedupTest {

    @Mock private MarketNewsResearchPort newsResearch;
    @Mock private MarketDataPort         marketData;
    @Mock private ImpactClassifierPort   impactClassifier;
    @Mock private MarketDigestRepository repository;
    @Mock private MarketDigestNotifier   notifier;
    @Mock private EntryEmbeddingPort     embeddingPort;

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);
    private static final double THRESHOLD = 0.93;

    // ─── cosineSimilarity unit tests ─────────────────────────────────────────

    @Test
    void cosineSimilarity_identicalVectors_returnsOne() {
        float[] v = {1.0f, 0.0f, 0.0f};
        assertThat(MarketDigestService.cosineSimilarity(v, v)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void cosineSimilarity_orthogonalVectors_returnsZero() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        assertThat(MarketDigestService.cosineSimilarity(a, b)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void cosineSimilarity_zeroVector_returnsZero() {
        float[] zero = {0.0f, 0.0f};
        float[] v    = {1.0f, 0.0f};
        assertThat(MarketDigestService.cosineSimilarity(zero, v)).isEqualTo(0.0);
    }

    // ─── deduplicateForNotification ──────────────────────────────────────────

    @Test
    void deduplicateForNotification_withNullEmbeddingPort_returnsAllCandidates() {
        MarketDigestService service = serviceWithEmbeddingPort(null);

        List<MarketDigestEntry> candidates = List.of(entry("Headline A"), entry("Headline B"));
        List<MarketDigestEntry> result = service.deduplicateForNotification(candidates, TODAY);

        assertThat(result).hasSize(2);
    }

    @Test
    void deduplicateForNotification_withNoRecentEntries_returnsAllCandidates() {
        MarketDigestService service = serviceWithEmbeddingPort(embeddingPort);
        when(repository.findByDateRange(any(), any())).thenReturn(List.of());

        List<MarketDigestEntry> candidates = List.of(entry("New Earnings Beat"));
        List<MarketDigestEntry> result = service.deduplicateForNotification(candidates, TODAY);

        assertThat(result).hasSize(1);
    }

    @Test
    void deduplicateForNotification_withHighSimilarity_suppressesCandidate() {
        MarketDigestService service = serviceWithEmbeddingPort(embeddingPort);

        MarketDigestEntry recentEntry = entry("Epic Health FDA Clearance");
        MarketDigest recentDigest = new MarketDigest(
                TODAY.minusDays(1), List.of(recentEntry), Instant.now());
        when(repository.findByDateRange(any(), any())).thenReturn(List.of(recentDigest));

        // Identical vectors → similarity = 1.0 > threshold
        float[] vec = {1.0f, 0.0f, 0.0f};
        when(embeddingPort.embed(any(), any())).thenReturn(vec);

        List<MarketDigestEntry> candidates = List.of(entry("Epic Health FDA Clearance (Resurface)"));
        List<MarketDigestEntry> result = service.deduplicateForNotification(candidates, TODAY);

        assertThat(result).isEmpty();
    }

    @Test
    void deduplicateForNotification_withLowSimilarity_includesCandidate() {
        MarketDigestService service = serviceWithEmbeddingPort(embeddingPort);

        MarketDigestEntry recentEntry = entry("Unrelated Story");
        MarketDigest recentDigest = new MarketDigest(
                TODAY.minusDays(1), List.of(recentEntry), Instant.now());
        when(repository.findByDateRange(any(), any())).thenReturn(List.of(recentDigest));

        // Orthogonal vectors → similarity = 0.0 < threshold
        when(embeddingPort.embed(eq("Unrelated Story"), any())).thenReturn(new float[]{1.0f, 0.0f});
        when(embeddingPort.embed(eq("Completely Different Story"), any())).thenReturn(new float[]{0.0f, 1.0f});

        List<MarketDigestEntry> candidates = List.of(entry("Completely Different Story"));
        List<MarketDigestEntry> result = service.deduplicateForNotification(candidates, TODAY);

        assertThat(result).hasSize(1);
    }

    @Test
    void deduplicateForNotification_whenCandidateEmbeddingFails_includesCandidate() {
        MarketDigestService service = serviceWithEmbeddingPort(embeddingPort);

        // Provide a recent entry so the dedup path is fully exercised
        MarketDigestEntry recentEntry = entry("Recent Story");
        MarketDigest recentDigest = new MarketDigest(
                TODAY.minusDays(1), List.of(recentEntry), Instant.now());
        when(repository.findByDateRange(any(), any())).thenReturn(List.of(recentDigest));

        // Recent entry embeds successfully; candidate embedding fails → fail-open, include it
        when(embeddingPort.embed(eq("Recent Story"), any())).thenReturn(new float[]{1.0f, 0.0f});
        when(embeddingPort.embed(eq("Some News"), any())).thenReturn(null);

        List<MarketDigestEntry> candidates = List.of(entry("Some News"));
        List<MarketDigestEntry> result = service.deduplicateForNotification(candidates, TODAY);

        assertThat(result).hasSize(1);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private MarketDigestService serviceWithEmbeddingPort(EntryEmbeddingPort port) {
        return new MarketDigestService(
                newsResearch, marketData, impactClassifier, repository, notifier, port, THRESHOLD);
    }

    private MarketDigestEntry entry(String headline) {
        MarketNewsItem item = new MarketNewsItem(
                headline, "Summary text.", List.of(),
                Instant.now(), NewsCategory.REGULATORY, null);
        return new MarketDigestEntry(
                item, List.of(), FactClassification.CONFIRMED,
                new MarketImpactRank(2), List.of());
    }
}
