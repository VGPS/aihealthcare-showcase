package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AnalystRatingChange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} integration tests for {@link AnalystRatingChangeAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@DataJpaTest
@Import(AnalystRatingChangeAdapter.class)
class AnalystRatingChangeAdapterTest {

    @Autowired
    private AnalystRatingChangeAdapter adapter;

    private static final Instant NOW      = Instant.now();
    private static final Instant MINUS_1H = NOW.minus(1, ChronoUnit.HOURS);
    private static final Instant MINUS_2H = NOW.minus(2, ChronoUnit.HOURS);

    @Test
    void findRecentChanges_whenEmpty_returnsEmptyList() {
        List<AnalystRatingChange> result = adapter.findRecentChanges("AAPL", MINUS_1H);
        assertThat(result).isEmpty();
    }

    @Test
    void saveAndFind_roundtripSucceeds() {
        AnalystRatingChange change = new AnalystRatingChange(
                "Goldman Sachs", "NVDA", "Neutral", "Buy",
                new BigDecimal("400.00"), new BigDecimal("550.00"), NOW);

        adapter.save(change);

        List<AnalystRatingChange> result = adapter.findRecentChanges("NVDA", MINUS_1H);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).firm()).isEqualTo("Goldman Sachs");
        assertThat(result.get(0).previousRating()).isEqualTo("Neutral");
        assertThat(result.get(0).newRating()).isEqualTo("Buy");
        assertThat(result.get(0).newPriceTarget()).isEqualByComparingTo(new BigDecimal("550.00"));
    }

    @Test
    void findRecentChanges_filtersByTicker() {
        adapter.save(new AnalystRatingChange("MS", "AAPL", "Hold", "Buy", null, null, NOW));
        adapter.save(new AnalystRatingChange("MS", "GOOG", "Sell", "Hold", null, null, NOW));

        List<AnalystRatingChange> appleOnly = adapter.findRecentChanges("AAPL", MINUS_1H);
        assertThat(appleOnly).hasSize(1);
        assertThat(appleOnly.get(0).tickerSymbol()).isEqualTo("AAPL");
    }

    @Test
    void findRecentChanges_respectsSinceCutoff() {
        adapter.save(new AnalystRatingChange("DB", "MSFT", "Hold", "Buy", null, null, MINUS_2H));
        adapter.save(new AnalystRatingChange("DB", "MSFT", "Buy", "Strong Buy", null, null, NOW));

        List<AnalystRatingChange> result = adapter.findRecentChanges("MSFT", MINUS_1H);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).newRating()).isEqualTo("Strong Buy");
    }

    @Test
    void save_nullPriceTargets_doesNotThrow() {
        AnalystRatingChange change = new AnalystRatingChange(
                "Citi", "AMZN", "Hold", "Buy", null, null, NOW);

        adapter.save(change);

        List<AnalystRatingChange> result = adapter.findRecentChanges("AMZN", MINUS_1H);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).previousPriceTarget()).isNull();
        assertThat(result.get(0).newPriceTarget()).isNull();
    }
}
