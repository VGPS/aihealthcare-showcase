package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataSet} — row-width assertion, truncation flag,
 * and defensive copying.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class DataSetTest {

    private static final List<DataColumn> TWO_COLUMNS = List.of(
            new DataColumn("id", "ID", "STRING"),
            new DataColumn("name", "Name", "STRING")
    );

    @Test
    void validDataSet_createsSuccessfully() {
        DataSet ds = new DataSet(
                TWO_COLUMNS,
                List.of(List.of("1", "Alice"), List.of("2", "Bob")),
                null, List.of(), List.of(), 0);

        assertThat(ds.columns()).hasSize(2);
        assertThat(ds.rows()).hasSize(2);
        assertThat(ds.truncatedAtRows()).isZero();
    }

    @Test
    void rowWidthMismatch_throws() {
        assertThatThrownBy(() -> new DataSet(
                TWO_COLUMNS,
                List.of(List.of("only-one")),
                null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Row width 1 does not match column count 2");
    }

    @Test
    void threeColumnRow_inTwoColumnDataSet_throws() {
        assertThatThrownBy(() -> new DataSet(
                TWO_COLUMNS,
                List.of(List.of("a", "b", "c")),
                null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void truncatedAtRows_preservedWhenSet() {
        DataSet ds = new DataSet(
                TWO_COLUMNS,
                List.of(List.of("1", "Alice")),
                null, null, null, 500);

        assertThat(ds.truncatedAtRows()).isEqualTo(500);
    }

    @Test
    void emptyRowsAndColumns_allowed() {
        DataSet ds = new DataSet(List.of(), List.of(), null, null, null, 0);

        assertThat(ds.columns()).isEmpty();
        assertThat(ds.rows()).isEmpty();
    }

    @Test
    void nullRows_becomesEmptyList() {
        DataSet ds = new DataSet(List.of(), null, null, null, null, 0);
        assertThat(ds.rows()).isEmpty();
    }

    @Test
    void nullColumns_becomesEmptyList() {
        DataSet ds = new DataSet(null, null, null, null, null, 0);
        assertThat(ds.columns()).isEmpty();
    }

    @Test
    void narrative_preservedForLlmSynthesis() {
        DataSet ds = new DataSet(
                List.of(), List.of(), "AI in healthcare is growing.", List.of(), List.of(), 0);

        assertThat(ds.narrative()).isEqualTo("AI in healthcare is growing.");
    }

    @Test
    void rows_areDefensivelyCopied() {
        List<String> row = List.of("1", "Alice");
        List<List<String>> rows = new java.util.ArrayList<>();
        rows.add(row);

        DataSet ds = new DataSet(TWO_COLUMNS, rows, null, null, null, 0);

        rows.add(List.of("2", "Bob"));

        assertThat(ds.rows()).hasSize(1);
    }

    @Test
    void warnings_preserved() {
        DataSet ds = new DataSet(
                List.of(), List.of(), null, null, List.of("Partial result"), 0);

        assertThat(ds.warnings()).containsExactly("Partial result");
    }
}
