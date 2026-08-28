package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.PriceReactionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PriceReactionService}.
 *
 * <p>Covers due/not-due horizon filtering, already-captured skipping, intraday
 * (quote-based) vs daily (bar-based) observed-price resolution, and the
 * fail-open behaviour when baseline or observed price is unavailable.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
@ExtendWith(MockitoExtension.class)
class PriceReactionServiceTest {

    @Mock
    private PriceReactionPort reactionPort;

    @Mock
    private MarketDataPort marketDataPort;

    private PriceReactionService service;

    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-18T20:00:00Z");
    private static final LocalDate PUBLISHED_DATE = PUBLISHED_AT.atZone(ZoneOffset.UTC).toLocalDate();

    @BeforeEach
    void setUp() {
        service = new PriceReactionService(reactionPort, marketDataPort);
    }

    @Test
    void capturePendingReactions_whenNoCandidates_returnsEmpty() {
        when(reactionPort.findTickerEntriesPublishedAfter(any())).thenReturn(List.of());

        List<PriceReactionSnapshot> result = service.capturePendingReactions(Instant.now());

        assertThat(result).isEmpty();
        verify(reactionPort, never()).save(any());
    }

    @Test
    void capturePendingReactions_onlyMeasuresDueHorizons() {
        TrackedCompanyEntry candidate = new TrackedCompanyEntry("entry-1", "DOCS", "Doximity", PUBLISHED_AT);
        when(reactionPort.findTickerEntriesPublishedAfter(any())).thenReturn(List.of(candidate));
        stubBaseline();
        stubQuote(new BigDecimal("10.80"));
        stubOneDayHistory();

        Instant now = PUBLISHED_AT.plus(Duration.ofDays(1)).plus(Duration.ofHours(2));
        List<PriceReactionSnapshot> result = service.capturePendingReactions(now);

        // ONE_HOUR, FOUR_HOUR, ONE_DAY are due at +1d2h; THREE_DAY is not.
        assertThat(result).hasSize(3);
        assertThat(result).extracting(PriceReactionSnapshot::horizon)
                .containsExactlyInAnyOrder(
                        ReactionHorizon.ONE_HOUR, ReactionHorizon.FOUR_HOUR, ReactionHorizon.ONE_DAY);
        verify(reactionPort, times(3)).save(any());
    }

    @Test
    void capturePendingReactions_intradayHorizon_usesLiveQuote() {
        TrackedCompanyEntry candidate = new TrackedCompanyEntry("entry-1", "DOCS", "Doximity", PUBLISHED_AT);
        when(reactionPort.findTickerEntriesPublishedAfter(any())).thenReturn(List.of(candidate));
        stubBaseline();
        stubQuote(new BigDecimal("10.80"));

        Instant now = PUBLISHED_AT.plus(Duration.ofHours(1));
        List<PriceReactionSnapshot> result = service.capturePendingReactions(now);

        assertThat(result).hasSize(1);
        PriceReactionSnapshot snapshot = result.get(0);
        assertThat(snapshot.horizon()).isEqualTo(ReactionHorizon.ONE_HOUR);
        assertThat(snapshot.baselinePrice()).isEqualByComparingTo("10.00");
        assertThat(snapshot.observedPrice()).isEqualByComparingTo("10.80");
        assertThat(snapshot.pctChange()).isEqualByComparingTo("8.00");
    }

    @Test
    void capturePendingReactions_dailyHorizon_usesClosingBarNotLiveQuote() {
        TrackedCompanyEntry candidate = new TrackedCompanyEntry("entry-1", "DOCS", "Doximity", PUBLISHED_AT);
        when(reactionPort.findTickerEntriesPublishedAfter(any())).thenReturn(List.of(candidate));
        stubBaseline();
        stubOneDayHistory();
        // Intraday horizons (ONE_HOUR/FOUR_HOUR) are also due at +1 day — force them
        // to miss so only the ONE_DAY snapshot (bar-based) is captured.
        when(marketDataPort.getQuote("DOCS")).thenReturn(Optional.empty());

        Instant now = PUBLISHED_AT.plus(Duration.ofDays(1));
        List<PriceReactionSnapshot> result = service.capturePendingReactions(now);

        assertThat(result).hasSize(1);
        PriceReactionSnapshot snapshot = result.get(0);
        assertThat(snapshot.horizon()).isEqualTo(ReactionHorizon.ONE_DAY);
        assertThat(snapshot.observedPrice()).isEqualByComparingTo("10.50");
        assertThat(snapshot.pctChange()).isEqualByComparingTo("5.00");
    }

    @Test
    void capturePendingReactions_alreadyCaptured_skipsHorizon() {
        TrackedCompanyEntry candidate = new TrackedCompanyEntry("entry-1", "DOCS", "Doximity", PUBLISHED_AT);
        when(reactionPort.findTickerEntriesPublishedAfter(any())).thenReturn(List.of(candidate));
        when(reactionPort.existsByEntryIdAndHorizon("entry-1", ReactionHorizon.ONE_HOUR)).thenReturn(true);

        Instant now = PUBLISHED_AT.plus(Duration.ofHours(1));
        List<PriceReactionSnapshot> result = service.capturePendingReactions(now);

        assertThat(result).isEmpty();
        verify(reactionPort, never()).save(any());
    }

    @Test
    void capturePendingReactions_noBaseline_skipsSilently() {
        TrackedCompanyEntry candidate = new TrackedCompanyEntry("entry-1", "DOCS", "Doximity", PUBLISHED_AT);
        when(reactionPort.findTickerEntriesPublishedAfter(any())).thenReturn(List.of(candidate));
        when(marketDataPort.getPriceHistory(eq("DOCS"), any(), any())).thenReturn(Optional.empty());

        Instant now = PUBLISHED_AT.plus(Duration.ofHours(1));
        List<PriceReactionSnapshot> result = service.capturePendingReactions(now);

        assertThat(result).isEmpty();
        verify(reactionPort, never()).save(any());
    }

    @Test
    void capturePendingReactions_noObservedPrice_skipsSilently() {
        TrackedCompanyEntry candidate = new TrackedCompanyEntry("entry-1", "DOCS", "Doximity", PUBLISHED_AT);
        when(reactionPort.findTickerEntriesPublishedAfter(any())).thenReturn(List.of(candidate));
        stubBaseline();
        when(marketDataPort.getQuote("DOCS")).thenReturn(Optional.empty());

        Instant now = PUBLISHED_AT.plus(Duration.ofHours(1));
        List<PriceReactionSnapshot> result = service.capturePendingReactions(now);

        assertThat(result).isEmpty();
        verify(reactionPort, never()).save(any());
    }

    // --- helpers ---

    private void stubBaseline() {
        PriceHistory history = new PriceHistory("DOCS", List.of(
                new PriceBar(PUBLISHED_DATE.minusDays(1), new BigDecimal("9.80"), new BigDecimal("10.10"),
                        new BigDecimal("9.70"), new BigDecimal("10.00"), 100_000L),
                new PriceBar(PUBLISHED_DATE, new BigDecimal("10.00"), new BigDecimal("10.20"),
                        new BigDecimal("9.90"), new BigDecimal("11.00"), 120_000L)
        ));
        when(marketDataPort.getPriceHistory(eq("DOCS"), any(), any())).thenReturn(Optional.of(history));
    }

    private void stubQuote(BigDecimal price) {
        when(marketDataPort.getQuote("DOCS"))
                .thenReturn(Optional.of(new Quote("DOCS", price, BigDecimal.ZERO, null)));
    }

    private void stubOneDayHistory() {
        LocalDate dueDate = PUBLISHED_DATE.plusDays(1);
        PriceHistory history = new PriceHistory("DOCS", List.of(
                new PriceBar(dueDate, new BigDecimal("10.20"), new BigDecimal("10.60"),
                        new BigDecimal("10.10"), new BigDecimal("10.50"), 150_000L)
        ));
        when(marketDataPort.getPriceHistory(eq("DOCS"), any(), eq(dueDate))).thenReturn(Optional.of(history));
    }
}
