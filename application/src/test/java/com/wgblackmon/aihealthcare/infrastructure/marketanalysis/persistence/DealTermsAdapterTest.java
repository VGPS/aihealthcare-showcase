package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.DealTerms;
import com.wgblackmon.aihealthcare.domain.marketanalysis.DisclosedPortion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} integration tests for {@link DealTermsAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@DataJpaTest
@Import(DealTermsAdapter.class)
class DealTermsAdapterTest {

    @Autowired
    private DealTermsAdapter adapter;

    private static final String HEADLINE = "Acme acquires BetaCo for $500M";

    @Test
    void findByEntryHeadline_whenNone_returnsEmpty() {
        Optional<DealTerms> result = adapter.findByEntryHeadline(HEADLINE);
        assertThat(result).isEmpty();
    }

    @Test
    void saveAndFind_roundtripSucceeds() {
        DealTerms terms = new DealTerms(
                200_000_000L,
                100_000_000L,
                new BigDecimal("15.0"),
                null,
                DisclosedPortion.PARTIAL, null
        );

        adapter.save(HEADLINE, terms);

        Optional<DealTerms> result = adapter.findByEntryHeadline(HEADLINE);
        assertThat(result).isPresent();
        assertThat(result.get().upfrontCashUsd()).isEqualTo(200_000_000L);
        assertThat(result.get().milestonePaymentsUsd()).isEqualTo(100_000_000L);
        assertThat(result.get().equityStakePct()).isEqualByComparingTo(new BigDecimal("15.0"));
        assertThat(result.get().royaltyPct()).isNull();
        assertThat(result.get().disclosedPortion()).isEqualTo(DisclosedPortion.PARTIAL);
    }

    @Test
    void save_replacesExistingTermsForSameHeadline() {
        DealTerms original = new DealTerms(
                100_000_000L, null, null, null, DisclosedPortion.UNDISCLOSED, null);
        DealTerms updated = new DealTerms(
                500_000_000L, null, null, null, DisclosedPortion.FULL, null);

        adapter.save(HEADLINE, original);
        adapter.save(HEADLINE, updated);

        Optional<DealTerms> result = adapter.findByEntryHeadline(HEADLINE);
        assertThat(result).isPresent();
        assertThat(result.get().upfrontCashUsd()).isEqualTo(500_000_000L);
        assertThat(result.get().disclosedPortion()).isEqualTo(DisclosedPortion.FULL);
    }

    @Test
    void save_allNullFinancials_succeeds() {
        DealTerms terms = new DealTerms(null, null, null, null, DisclosedPortion.UNDISCLOSED, null);

        adapter.save(HEADLINE, terms);

        Optional<DealTerms> result = adapter.findByEntryHeadline(HEADLINE);
        assertThat(result).isPresent();
        assertThat(result.get().upfrontCashUsd()).isNull();
        assertThat(result.get().milestonePaymentsUsd()).isNull();
        assertThat(result.get().equityStakePct()).isNull();
    }

    @Test
    void findByEntryHeadline_differentiatesByHeadline() {
        adapter.save("Deal One", new DealTerms(10L, null, null, null, DisclosedPortion.FULL, null));
        adapter.save("Deal Two", new DealTerms(20L, null, null, null, DisclosedPortion.FULL, null));

        Optional<DealTerms> one = adapter.findByEntryHeadline("Deal One");
        assertThat(one).isPresent();
        assertThat(one.get().upfrontCashUsd()).isEqualTo(10L);

        Optional<DealTerms> two = adapter.findByEntryHeadline("Deal Two");
        assertThat(two).isPresent();
        assertThat(two.get().upfrontCashUsd()).isEqualTo(20L);
    }
}
