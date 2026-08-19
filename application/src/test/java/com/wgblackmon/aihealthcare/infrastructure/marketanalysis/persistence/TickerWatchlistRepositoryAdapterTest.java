package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} integration tests for {@link TickerWatchlistRepositoryAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@DataJpaTest
@Import(TickerWatchlistRepositoryAdapter.class)
class TickerWatchlistRepositoryAdapterTest {

    @Autowired
    private TickerWatchlistRepositoryAdapter adapter;

    private static final String USER_A = "alice@example.com";
    private static final String USER_B = "bob@example.com";

    @Test
    void findWatchedTickers_whenEmpty_returnsEmptyList() {
        List<String> result = adapter.findWatchedTickers(USER_A);
        assertThat(result).isEmpty();
    }

    @Test
    void addTicker_andFind_roundtripSucceeds() {
        adapter.addTicker(USER_A, "NVDA");
        adapter.addTicker(USER_A, "AAPL");

        List<String> result = adapter.findWatchedTickers(USER_A);
        assertThat(result).containsExactlyInAnyOrder("NVDA", "AAPL");
    }

    @Test
    void addTicker_idempotent_noDuplicates() {
        adapter.addTicker(USER_A, "MSFT");
        adapter.addTicker(USER_A, "MSFT");

        List<String> result = adapter.findWatchedTickers(USER_A);
        assertThat(result).hasSize(1).containsExactly("MSFT");
    }

    @Test
    void removeTicker_removesPresentTicker() {
        adapter.addTicker(USER_A, "GOOG");
        adapter.addTicker(USER_A, "META");

        adapter.removeTicker(USER_A, "GOOG");

        List<String> result = adapter.findWatchedTickers(USER_A);
        assertThat(result).containsExactly("META");
    }

    @Test
    void removeTicker_whenAbsent_doesNotThrow() {
        adapter.removeTicker(USER_A, "UNKNOWN");
        assertThat(adapter.findWatchedTickers(USER_A)).isEmpty();
    }

    @Test
    void replaceWatchlist_replacesAllTickers() {
        adapter.addTicker(USER_A, "OLD1");
        adapter.addTicker(USER_A, "OLD2");

        adapter.replaceWatchlist(USER_A, List.of("NEW1", "NEW2", "NEW3"));

        List<String> result = adapter.findWatchedTickers(USER_A);
        assertThat(result).containsExactlyInAnyOrder("NEW1", "NEW2", "NEW3");
    }

    @Test
    void replaceWatchlist_withEmptyList_clearsWatchlist() {
        adapter.addTicker(USER_A, "AMZN");

        adapter.replaceWatchlist(USER_A, List.of());

        assertThat(adapter.findWatchedTickers(USER_A)).isEmpty();
    }

    @Test
    void watchlists_areIsolatedBySubscriber() {
        adapter.addTicker(USER_A, "AAPL");
        adapter.addTicker(USER_B, "MSFT");

        assertThat(adapter.findWatchedTickers(USER_A)).containsExactly("AAPL");
        assertThat(adapter.findWatchedTickers(USER_B)).containsExactly("MSFT");
    }
}
