package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Describes a single parameter accepted by a data feed or canned prompt.
 *
 * <p>Used to build dynamic forms on the console page and to validate
 * customer-supplied parameter values before execution. When {@code type}
 * is {@code "ENUM"}, {@code allowedValues} must be non-empty.
 *
 * @param name          machine-readable parameter key (non-blank)
 * @param label         human-readable label for the console form
 * @param type          one of STRING, INTEGER, DATE, ENUM
 * @param required      whether the parameter must be supplied
 * @param defaultValue  default value when not supplied; may be null
 * @param allowedValues permitted values when type is ENUM; empty otherwise
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataParameter(
        String name,
        String label,
        String type,
        boolean required,
        String defaultValue,
        List<String> allowedValues
) {
    public DataParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DataParameter name must not be blank");
        }
        if ("ENUM".equals(type) && (allowedValues == null || allowedValues.isEmpty())) {
            throw new IllegalArgumentException("ENUM parameter '" + name + "' requires non-empty allowedValues");
        }
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }
}
