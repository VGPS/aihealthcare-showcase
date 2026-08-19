package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.PeerGroup;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PrivateFundingRound;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} integration tests for {@link PrivateFundingRoundAdapter}.
 *
 * <p>Runs against the real Postgres test database ({@code aihealthcaredb_test}).
 * Each test runs in a rolled-back transaction.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@DataJpaTest
@Import(PrivateFundingRoundAdapter.class)
class PrivateFundingRoundAdapterTest {

    @Autowired
    private PrivateFundingRoundAdapter adapter;

    private static final Instant NOW   = Instant.now();
    private static final Instant MINUS_1H = NOW.minus(1, ChronoUnit.HOURS);
    private static final Instant MINUS_2H = NOW.minus(2, ChronoUnit.HOURS);

    @Test
    void findRecentRounds_whenEmpty_returnsEmptyList() {
        List<PrivateFundingRound> result = adapter.findRecentRounds(MINUS_1H, null);
        assertThat(result).isEmpty();
    }

    @Test
    void saveAndFind_roundtripSucceeds() {
        PrivateFundingRound round = new PrivateFundingRound(
                "Acme Health AI", "Series B",
                100_000_000L,
                List.of("Andreessen Horowitz", "General Catalyst"),
                NOW
        );

        adapter.save(round, PeerGroup.AI_SCRIBE_DOCUMENTATION);

        List<PrivateFundingRound> result = adapter.findRecentRounds(MINUS_1H, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).companyName()).isEqualTo("Acme Health AI");
        assertThat(result.get(0).roundStage()).isEqualTo("Series B");
        assertThat(result.get(0).amountUsd()).isEqualTo(100_000_000L);
        assertThat(result.get(0).leadInvestors()).containsExactlyInAnyOrder(
                "Andreessen Horowitz", "General Catalyst");
    }

    @Test
    void findRecentRounds_filterByPeerGroup() {
        adapter.save(new PrivateFundingRound("Scribe Co", "A", 50_000_000L, List.of(), NOW),
                PeerGroup.AI_SCRIBE_DOCUMENTATION);
        adapter.save(new PrivateFundingRound("Imaging Co", "A", 60_000_000L, List.of(), NOW),
                PeerGroup.DIAGNOSTIC_IMAGING_AI);

        List<PrivateFundingRound> scribes = adapter.findRecentRounds(MINUS_1H,
                PeerGroup.AI_SCRIBE_DOCUMENTATION);
        assertThat(scribes).hasSize(1);
        assertThat(scribes.get(0).companyName()).isEqualTo("Scribe Co");
    }

    @Test
    void findRecentRounds_respectsSinceCutoff() {
        adapter.save(new PrivateFundingRound("Old Co", "A", null, List.of(), MINUS_2H),
                PeerGroup.OTHER);
        adapter.save(new PrivateFundingRound("New Co", "B", null, List.of(), NOW),
                PeerGroup.OTHER);

        List<PrivateFundingRound> result = adapter.findRecentRounds(MINUS_1H, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).companyName()).isEqualTo("New Co");
    }

    @Test
    void save_nullAmountAndLeadInvestors_doesNotThrow() {
        PrivateFundingRound round = new PrivateFundingRound(
                "Stealth Co", "Seed", null, List.of(), NOW);

        adapter.save(round, PeerGroup.DRUG_DISCOVERY_AI);

        List<PrivateFundingRound> result = adapter.findRecentRounds(MINUS_1H, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).amountUsd()).isNull();
        assertThat(result.get(0).leadInvestors()).isEmpty();
    }
}
