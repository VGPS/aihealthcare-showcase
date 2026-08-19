package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactAssessment;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDimension;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDirection;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketImpactRank;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PeerGroup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest integration tests for {@link MarketDigestRepositoryAdapter}.
 *
 * <p>Exercises the full save → findByDate roundtrip against the real Postgres
 * test database ({@code aihealthcaredb_test}). Each test runs in a transaction
 * that rolls back on completion.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@DataJpaTest
@Import(MarketDigestRepositoryAdapter.class)
class MarketDigestRepositoryAdapterTest {

    @Autowired
    private MarketDigestRepositoryAdapter adapter;

    @Autowired
    private MarketDigestJpaRepository digestRepo;

    @Autowired
    private MarketDigestEntryJpaRepository entryRepo;

    @Autowired
    private MarketDigestImpactAssessmentJpaRepository assessmentRepo;

    @Autowired
    private MarketDigestAffectedCompanyJpaRepository companyRepo;

    // ─── save + findByDate roundtrip ────────────────────────────────────────

    @Test
    void save_andFindByDate_roundtripsAllFields() {
        LocalDate date = LocalDate.of(2026, 8, 19);
        MarketDigest digest = buildDigestWithOneFullEntry(date);

        adapter.save(digest);

        Optional<MarketDigest> found = adapter.findByDate(date);
        assertThat(found).isPresent();

        MarketDigest loaded = found.get();
        assertThat(loaded.date()).isEqualTo(date);
        assertThat(loaded.entries()).hasSize(1);

        MarketDigestEntry entry = loaded.entries().get(0);
        assertThat(entry.newsItem().headline()).isEqualTo("Epic acquires AI startup");
        assertThat(entry.newsItem().summary()).isEqualTo("Epic Systems agreed to acquire an AI diagnostics startup.");
        assertThat(entry.newsItem().sourceUrls()).containsExactly("https://source1.com", "https://source2.com");
        assertThat(entry.newsItem().category()).isEqualTo(NewsCategory.M_AND_A);
        assertThat(entry.newsItem().dealSizeUsd()).isEqualTo(120_000_000L);
        assertThat(entry.factClassification()).isEqualTo(FactClassification.CONFIRMED);
        assertThat(entry.rank().value()).isEqualTo(1);

        assertThat(entry.impactAssessments()).hasSize(1);
        ImpactAssessment assessment = entry.impactAssessments().get(0);
        assertThat(assessment.dimension()).isEqualTo(ImpactDimension.VALUATION);
        assertThat(assessment.direction()).isEqualTo(ImpactDirection.POSITIVE);
        assertThat(assessment.rationale()).isEqualTo("Acquisition adds AI capability to Epic's platform.");

        assertThat(entry.affectedCompanies()).hasSize(1);
        AffectedCompany company = entry.affectedCompanies().get(0);
        assertThat(company.name()).isEqualTo("Epic Systems");
        assertThat(company.tickerSymbol()).isNull();
        assertThat(company.role()).isEqualTo("acquirer");
        assertThat(company.peerGroup()).isEqualTo(PeerGroup.VALUE_BASED_CARE_PLATFORM);
    }

    // ─── empty result ────────────────────────────────────────────────────────

    @Test
    void findByDate_nonExistentDate_returnsEmpty() {
        Optional<MarketDigest> result = adapter.findByDate(LocalDate.of(2020, 1, 1));
        assertThat(result).isEmpty();
    }

    // ─── empty entries ────────────────────────────────────────────────────────

    @Test
    void save_withNoEntries_persistsEmptyDigest() {
        LocalDate date = LocalDate.of(2026, 8, 18);
        MarketDigest digest = MarketDigest.empty(date);

        adapter.save(digest);

        Optional<MarketDigest> found = adapter.findByDate(date);
        assertThat(found).isPresent();
        assertThat(found.get().entries()).isEmpty();
        assertThat(digestRepo.findByDigestDate(date)).isPresent();
    }

    // ─── upsert ──────────────────────────────────────────────────────────────

    @Test
    void save_sameDate_upserts_secondVersionWins() {
        LocalDate date = LocalDate.of(2026, 8, 17);

        // First save
        MarketDigest first = buildDigestWithHeadline(date, "First headline");
        adapter.save(first);

        // Second save — same date, different headline
        MarketDigest second = buildDigestWithHeadline(date, "Second headline");
        adapter.save(second);

        Optional<MarketDigest> found = adapter.findByDate(date);
        assertThat(found).isPresent();
        assertThat(found.get().entries()).hasSize(1);
        assertThat(found.get().entries().get(0).newsItem().headline()).isEqualTo("Second headline");

        // Only one digest row should exist
        assertThat(digestRepo.findAll()).hasSize(1);
    }

    // ─── multiple entries ────────────────────────────────────────────────────

    @Test
    void save_withMultipleEntries_persistsAll() {
        LocalDate date = LocalDate.of(2026, 8, 16);

        MarketDigestEntry entry1 = buildEntry("Earnings beat Q2", NewsCategory.EARNINGS);
        MarketDigestEntry entry2 = buildEntry("FDA clears AI diagnostic", NewsCategory.REGULATORY);
        MarketDigestEntry entry3 = buildEntry("Merger announced", NewsCategory.M_AND_A);

        MarketDigest digest = new MarketDigest(date, List.of(entry1, entry2, entry3), Instant.now());
        adapter.save(digest);

        Optional<MarketDigest> found = adapter.findByDate(date);
        assertThat(found).isPresent();
        assertThat(found.get().entries()).hasSize(3);
    }

    // ─── nullable company ticker ─────────────────────────────────────────────

    @Test
    void save_companyWithNullTickerAndNullPeerGroup_roundtrips() {
        LocalDate date = LocalDate.of(2026, 8, 15);

        AffectedCompany privateCompany = new AffectedCompany("Private Co", null, "target", null);
        MarketDigestEntry entry = new MarketDigestEntry(
                newsItem("Private deal", NewsCategory.M_AND_A),
                List.of(),
                FactClassification.SPECULATIVE,
                new MarketImpactRank(3),
                List.of(privateCompany)
        );

        adapter.save(new MarketDigest(date, List.of(entry), Instant.now()));

        Optional<MarketDigest> found = adapter.findByDate(date);
        assertThat(found).isPresent();
        AffectedCompany loaded = found.get().entries().get(0).affectedCompanies().get(0);
        assertThat(loaded.tickerSymbol()).isNull();
        assertThat(loaded.peerGroup()).isNull();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private MarketDigest buildDigestWithOneFullEntry(LocalDate date) {
        ImpactAssessment assessment = new ImpactAssessment(
                ImpactDimension.VALUATION,
                ImpactDirection.POSITIVE,
                "Acquisition adds AI capability to Epic's platform."
        );
        AffectedCompany company = new AffectedCompany(
                "Epic Systems", null, "acquirer", PeerGroup.VALUE_BASED_CARE_PLATFORM
        );
        MarketNewsItem item = new MarketNewsItem(
                "Epic acquires AI startup",
                "Epic Systems agreed to acquire an AI diagnostics startup.",
                List.of("https://source1.com", "https://source2.com"),
                Instant.now(),
                NewsCategory.M_AND_A,
                120_000_000L
        );
        MarketDigestEntry entry = new MarketDigestEntry(
                item,
                List.of(assessment),
                FactClassification.CONFIRMED,
                new MarketImpactRank(1),
                List.of(company)
        );
        return new MarketDigest(date, List.of(entry), Instant.now());
    }

    private MarketDigest buildDigestWithHeadline(LocalDate date, String headline) {
        return new MarketDigest(date, List.of(buildEntry(headline, NewsCategory.EARNINGS)), Instant.now());
    }

    private MarketDigestEntry buildEntry(String headline, NewsCategory category) {
        return new MarketDigestEntry(
                newsItem(headline, category),
                List.of(),
                FactClassification.CONFIRMED,
                new MarketImpactRank(2),
                List.of()
        );
    }

    private MarketNewsItem newsItem(String headline, NewsCategory category) {
        return new MarketNewsItem(
                headline,
                "Summary for " + headline,
                List.of(),
                Instant.now(),
                category,
                null
        );
    }
}
