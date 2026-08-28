package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionSnapshot;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ReactionHorizon;
import com.wgblackmon.aihealthcare.domain.marketanalysis.TrackedCompanyEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} integration tests for {@link PriceReactionAdapter}.
 *
 * <p>Runs against the real Postgres test database ({@code aihealthcaredb_test})
 * using JPA schema auto-creation. Each test runs in a rolled-back transaction.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
@DataJpaTest
@Import(PriceReactionAdapter.class)
class PriceReactionAdapterTest {

    @Autowired
    private PriceReactionAdapter adapter;

    @Autowired
    private MarketDigestEntryJpaRepository entryRepo;

    @Autowired
    private MarketDigestAffectedCompanyJpaRepository companyRepo;

    private static final Instant NOW = Instant.now();

    @Test
    void save_and_findByEntryId_roundTrips() {
        String entryId = newEntry(NOW);

        PriceReactionSnapshot snapshot = new PriceReactionSnapshot(
                entryId, "DOCS", ReactionHorizon.ONE_DAY,
                new BigDecimal("10.00"), new BigDecimal("10.80"),
                new BigDecimal("8.00"), NOW);
        adapter.save(snapshot);

        List<PriceReactionSnapshot> result = adapter.findByEntryId(entryId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).tickerSymbol()).isEqualTo("DOCS");
        assertThat(result.get(0).horizon()).isEqualTo(ReactionHorizon.ONE_DAY);
        assertThat(result.get(0).baselinePrice()).isEqualByComparingTo("10.00");
        assertThat(result.get(0).observedPrice()).isEqualByComparingTo("10.80");
        assertThat(result.get(0).pctChange()).isEqualByComparingTo("8.00");
    }

    @Test
    void findByEntryId_whenNoneSaved_returnsEmpty() {
        assertThat(adapter.findByEntryId("nonexistent-entry")).isEmpty();
    }

    @Test
    void existsByEntryIdAndHorizon_falseBeforeSave() {
        String entryId = newEntry(NOW);
        assertThat(adapter.existsByEntryIdAndHorizon(entryId, ReactionHorizon.ONE_HOUR)).isFalse();
    }

    @Test
    void existsByEntryIdAndHorizon_trueAfterSave() {
        String entryId = newEntry(NOW);
        adapter.save(new PriceReactionSnapshot(
                entryId, "DOCS", ReactionHorizon.ONE_HOUR,
                new BigDecimal("10.00"), new BigDecimal("10.10"),
                new BigDecimal("1.00"), NOW));

        assertThat(adapter.existsByEntryIdAndHorizon(entryId, ReactionHorizon.ONE_HOUR)).isTrue();
        assertThat(adapter.existsByEntryIdAndHorizon(entryId, ReactionHorizon.FOUR_HOUR)).isFalse();
    }

    @Test
    void findTickerEntriesPublishedAfter_returnsTickerBearingCompanies() {
        Instant publishedAt = NOW.minus(1, ChronoUnit.HOURS);
        String entryId = newEntry(publishedAt);
        newCompany(entryId, "Doximity", "DOCS");

        List<TrackedCompanyEntry> result =
                adapter.findTickerEntriesPublishedAfter(NOW.minus(1, ChronoUnit.DAYS));

        assertThat(result).extracting(TrackedCompanyEntry::entryId).contains(entryId);
        assertThat(result).extracting(TrackedCompanyEntry::tickerSymbol).contains("DOCS");
    }

    @Test
    void findTickerEntriesPublishedAfter_excludesCompaniesWithNullTicker() {
        Instant publishedAt = NOW.minus(1, ChronoUnit.HOURS);
        String entryId = newEntry(publishedAt);
        newCompany(entryId, "Private Health Co", null);

        List<TrackedCompanyEntry> result =
                adapter.findTickerEntriesPublishedAfter(NOW.minus(1, ChronoUnit.DAYS));

        assertThat(result).extracting(TrackedCompanyEntry::entryId).doesNotContain(entryId);
    }

    @Test
    void findTickerEntriesPublishedAfter_excludesEntriesBeforeSince() {
        Instant publishedAt = NOW.minus(10, ChronoUnit.DAYS);
        String entryId = newEntry(publishedAt);
        newCompany(entryId, "Doximity", "DOCS");

        List<TrackedCompanyEntry> result =
                adapter.findTickerEntriesPublishedAfter(NOW.minus(1, ChronoUnit.DAYS));

        assertThat(result).extracting(TrackedCompanyEntry::entryId).doesNotContain(entryId);
    }

    @Test
    void findByTickerAndPublishedAt_returnsSnapshotsForMatchingEntry() {
        Instant publishedAt = NOW.minus(2, ChronoUnit.DAYS);
        String entryId = newEntry(publishedAt);
        newCompany(entryId, "Doximity", "DOCS");
        adapter.save(new PriceReactionSnapshot(
                entryId, "DOCS", ReactionHorizon.ONE_DAY,
                new BigDecimal("10.00"), new BigDecimal("10.80"),
                new BigDecimal("8.00"), NOW));

        List<PriceReactionSnapshot> result = adapter.findByTickerAndPublishedAt("DOCS", publishedAt);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).entryId()).isEqualTo(entryId);
        assertThat(result.get(0).horizon()).isEqualTo(ReactionHorizon.ONE_DAY);
    }

    @Test
    void findByTickerAndPublishedAt_whenNoMatchingEntry_returnsEmpty() {
        assertThat(adapter.findByTickerAndPublishedAt("NOPE", NOW)).isEmpty();
    }

    @Test
    void findByTickerAndPublishedAt_whenEntryExistsButNoSnapshotYet_returnsEmpty() {
        Instant publishedAt = NOW.minus(1, ChronoUnit.HOURS);
        String entryId = newEntry(publishedAt);
        newCompany(entryId, "Doximity", "DOCS");

        assertThat(adapter.findByTickerAndPublishedAt("DOCS", publishedAt)).isEmpty();
    }

    // --- helpers ---

    private String newEntry(Instant publishedAt) {
        String entryId = UUID.randomUUID().toString();
        entryRepo.save(new MarketDigestEntryEntity(
                entryId, UUID.randomUUID().toString(), "Test headline", "Test summary",
                "https://example.com", "EARNINGS", "CONFIRMED", 1, publishedAt, null));
        return entryId;
    }

    private void newCompany(String entryId, String companyName, String tickerSymbol) {
        companyRepo.save(new MarketDigestAffectedCompanyEntity(
                UUID.randomUUID().toString(), entryId, companyName, tickerSymbol,
                "earnings subject", null, null, null, null));
    }
}
