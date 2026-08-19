package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.GuidanceComparison;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} integration tests for {@link GuidanceHistoryAdapter}.
 *
 * <p>Runs against the real Postgres test database ({@code aihealthcaredb_test})
 * using JPA schema auto-creation. Each test runs in a rolled-back transaction.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@DataJpaTest
@Import(GuidanceHistoryAdapter.class)
class GuidanceHistoryAdapterTest {

    @Autowired
    private GuidanceHistoryAdapter adapter;

    private static final BigDecimal LOW  = new BigDecimal("1.20");
    private static final BigDecimal HIGH = new BigDecimal("1.40");
    private static final BigDecimal NEW_LOW  = new BigDecimal("1.50");
    private static final BigDecimal NEW_HIGH = new BigDecimal("1.60");

    @Test
    void getPriorGuidance_whenNoHistory_returnsEmpty() {
        Optional<GuidanceComparison> result = adapter.getPriorGuidance("NVDA", "EPS");
        assertThat(result).isEmpty();
    }

    @Test
    void recordAndRetrieve_returnsMostRecent() {
        GuidanceComparison g = new GuidanceComparison("AAPL", LOW, HIGH, NEW_LOW, NEW_HIGH, "EPS");

        adapter.recordGuidance(g);

        Optional<GuidanceComparison> result = adapter.getPriorGuidance("AAPL", "EPS");
        assertThat(result).isPresent();
        assertThat(result.get().tickerSymbol()).isEqualTo("AAPL");
        assertThat(result.get().priorGuidanceLow()).isEqualByComparingTo(LOW);
        assertThat(result.get().newGuidanceHigh()).isEqualByComparingTo(NEW_HIGH);
        assertThat(result.get().metric()).isEqualTo("EPS");
    }

    @Test
    void getPriorGuidance_differentiatesByMetric() {
        adapter.recordGuidance(new GuidanceComparison("MSFT", LOW, HIGH, NEW_LOW, NEW_HIGH, "EPS"));
        adapter.recordGuidance(new GuidanceComparison("MSFT", LOW, HIGH,
                new BigDecimal("500"), new BigDecimal("510"), "REVENUE"));

        Optional<GuidanceComparison> eps     = adapter.getPriorGuidance("MSFT", "EPS");
        Optional<GuidanceComparison> revenue = adapter.getPriorGuidance("MSFT", "REVENUE");

        assertThat(eps).isPresent();
        assertThat(eps.get().newGuidanceLow()).isEqualByComparingTo(NEW_LOW);

        assertThat(revenue).isPresent();
        assertThat(revenue.get().newGuidanceLow()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void getPriorGuidance_returnsMostRecentWhenMultipleRows() {
        GuidanceComparison older = new GuidanceComparison("GOOG", LOW, HIGH, LOW, HIGH, "EPS");
        GuidanceComparison newer = new GuidanceComparison("GOOG", LOW, HIGH,
                new BigDecimal("1.80"), new BigDecimal("1.90"), "EPS");

        adapter.recordGuidance(older);
        adapter.recordGuidance(newer);

        Optional<GuidanceComparison> result = adapter.getPriorGuidance("GOOG", "EPS");
        assertThat(result).isPresent();
        assertThat(result.get().newGuidanceLow()).isEqualByComparingTo(new BigDecimal("1.80"));
    }

    @Test
    void getPriorGuidance_differentiatesByTicker() {
        adapter.recordGuidance(new GuidanceComparison("AAPL", LOW, HIGH, LOW, HIGH, "EPS"));
        adapter.recordGuidance(new GuidanceComparison("MSFT", LOW, HIGH,
                new BigDecimal("3.00"), new BigDecimal("3.10"), "EPS"));

        Optional<GuidanceComparison> appleResult = adapter.getPriorGuidance("AAPL", "EPS");
        assertThat(appleResult).isPresent();
        assertThat(appleResult.get().tickerSymbol()).isEqualTo("AAPL");
        assertThat(appleResult.get().newGuidanceLow()).isEqualByComparingTo(LOW);
    }
}
