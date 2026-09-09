package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataQueryPlan} — the security boundary record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class DataQueryPlanTest {

    @Test
    void validPlan_createsSuccessfully() {
        DataQueryPlan plan = new DataQueryPlan(
                "legislation",
                List.of("AI", "healthcare"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of("MT", "CA"),
                List.of("ENACTED"),
                "yearEnacted",
                50);

        assertThat(plan.feedId()).isEqualTo("legislation");
        assertThat(plan.keywords()).containsExactly("AI", "healthcare");
        assertThat(plan.states()).containsExactly("MT", "CA");
        assertThat(plan.categories()).containsExactly("ENACTED");
        assertThat(plan.limit()).isEqualTo(50);
    }

    @Test
    void zeroLimit_throws() {
        assertThatThrownBy(() -> new DataQueryPlan(
                "articles", null, null, null, null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be positive");
    }

    @Test
    void negativeLimit_throws() {
        assertThatThrownBy(() -> new DataQueryPlan(
                "articles", null, null, null, null, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullLists_becomeEmpty() {
        DataQueryPlan plan = new DataQueryPlan(
                "regulatory", null, null, null, null, null, null, 10);

        assertThat(plan.keywords()).isEmpty();
        assertThat(plan.states()).isEmpty();
        assertThat(plan.categories()).isEmpty();
    }

    @Test
    void lists_areDefensivelyCopied() {
        java.util.ArrayList<String> mutable = new java.util.ArrayList<>();
        mutable.add("MT");

        DataQueryPlan plan = new DataQueryPlan(
                "legislation", null, null, null, mutable, null, null, 10);

        mutable.add("CA");

        assertThat(plan.states()).containsExactly("MT");
    }

    @Test
    void minimalPlan_onlyFeedIdAndLimit() {
        DataQueryPlan plan = new DataQueryPlan(
                "articles", null, null, null, null, null, null, 100);

        assertThat(plan.feedId()).isEqualTo("articles");
        assertThat(plan.dateFrom()).isNull();
        assertThat(plan.dateTo()).isNull();
        assertThat(plan.sortBy()).isNull();
    }

    @Test
    void dateRange_preserved() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 9, 1);

        DataQueryPlan plan = new DataQueryPlan(
                "articles", null, from, to, null, null, "publishedAt", 25);

        assertThat(plan.dateFrom()).isEqualTo(from);
        assertThat(plan.dateTo()).isEqualTo(to);
        assertThat(plan.sortBy()).isEqualTo("publishedAt");
    }
}
