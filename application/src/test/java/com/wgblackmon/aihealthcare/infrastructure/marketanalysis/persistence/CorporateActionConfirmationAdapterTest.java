package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.CorporateActionConfirmation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA integration tests for {@link CorporateActionConfirmationAdapter}.
 *
 * <p>Uses {@link DataJpaTest} against the test PostgreSQL database to verify
 * round-trip persistence for {@link CorporateActionConfirmation} records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@DataJpaTest
@Import(CorporateActionConfirmationAdapter.class)
class CorporateActionConfirmationAdapterTest {

    @Autowired
    private CorporateActionConfirmationAdapter adapter;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 19);

    @Test
    void save_andFindByDigestDate_roundTrip() {
        CorporateActionConfirmation confirmation = new CorporateActionConfirmation(
                "conf-001", DATE, "DOCS", "cash_dividend",
                DATE.minusDays(10), DATE, DATE.plusDays(1), DATE.plusDays(5));

        adapter.save(confirmation);

        List<CorporateActionConfirmation> found = adapter.findByDigestDate(DATE);
        assertThat(found).hasSize(1);
        CorporateActionConfirmation result = found.get(0);
        assertThat(result.confirmationId()).isEqualTo("conf-001");
        assertThat(result.tickerSymbol()).isEqualTo("DOCS");
        assertThat(result.actionType()).isEqualTo("cash_dividend");
        assertThat(result.digestDate()).isEqualTo(DATE);
    }

    @Test
    void save_withNullOptionalDates_roundTrip() {
        CorporateActionConfirmation confirmation = new CorporateActionConfirmation(
                "conf-002", DATE, "EVH", "cash_merger",
                DATE, null, null, null);

        adapter.save(confirmation);

        List<CorporateActionConfirmation> found = adapter.findByDigestDate(DATE);
        CorporateActionConfirmation result = found.stream()
                .filter(c -> "conf-002".equals(c.confirmationId()))
                .findFirst()
                .orElseThrow();
        assertThat(result.exDate()).isNull();
        assertThat(result.recordDate()).isNull();
        assertThat(result.payableDate()).isNull();
    }

    @Test
    void findByDigestDate_wrongDate_returnsEmpty() {
        CorporateActionConfirmation confirmation = new CorporateActionConfirmation(
                "conf-003", DATE, "AMWL", "spinoff",
                DATE, null, null, null);

        adapter.save(confirmation);

        List<CorporateActionConfirmation> found = adapter.findByDigestDate(DATE.plusDays(1));
        assertThat(found).isEmpty();
    }

    @Test
    void save_multipleConfirmations_allFound() {
        adapter.save(new CorporateActionConfirmation(
                "conf-004", DATE, "DOCS", "cash_dividend", DATE, null, null, null));
        adapter.save(new CorporateActionConfirmation(
                "conf-005", DATE, "EVH", "cash_merger", DATE, null, null, null));

        List<CorporateActionConfirmation> found = adapter.findByDigestDate(DATE);
        assertThat(found).hasSize(2);
    }
}
