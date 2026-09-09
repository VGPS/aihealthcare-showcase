package com.wgblackmon.aihealthcare.domain.model;

/**
 * Describes a single column in a {@link DataSet} result.
 *
 * @param name  machine-readable column identifier
 * @param label human-readable column heading
 * @param type  data type hint (e.g. STRING, INTEGER, DATE, URI)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataColumn(
        String name,
        String label,
        String type
) {
}
