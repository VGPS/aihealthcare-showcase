package com.wgblackmon.aihealthcare.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The tabular result of an enterprise data feed fetch, optionally
 * accompanied by an LLM-generated narrative and source citations.
 *
 * <p>Every row must have exactly as many cells as there are columns.
 * {@code truncatedAtRows} is non-zero when the underlying result exceeded
 * the row limit and was clipped — the value records the original count
 * before truncation.
 *
 * @param columns         column definitions
 * @param rows            row data (each inner list has one cell per column)
 * @param narrative       LLM prose answer for {@code LLM_SYNTHESIS} feeds; null otherwise
 * @param citations       source citations (may be empty)
 * @param warnings        non-fatal issues encountered during fetch
 * @param truncatedAtRows original row count before truncation; 0 if not truncated
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataSet(
        List<DataColumn> columns,
        List<List<String>> rows,
        String narrative,
        List<SourceCitation> citations,
        List<String> warnings,
        int truncatedAtRows
) {
    public DataSet {
        columns = columns == null ? List.of() : List.copyOf(columns);
        citations = citations == null ? List.of() : List.copyOf(citations);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);

        if (rows == null) {
            rows = List.of();
        } else {
            int width = columns.size();
            List<List<String>> defensiveCopy = new ArrayList<>(rows.size());
            for (List<String> row : rows) {
                if (row.size() != width) {
                    throw new IllegalArgumentException(
                            "Row width " + row.size() + " does not match column count " + width);
                }
                defensiveCopy.add(List.copyOf(row));
            }
            rows = List.copyOf(defensiveCopy);
        }
    }
}
