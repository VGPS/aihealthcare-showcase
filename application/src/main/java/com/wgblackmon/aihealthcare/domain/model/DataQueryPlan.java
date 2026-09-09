package com.wgblackmon.aihealthcare.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * A constrained, whitelisted query plan — the security boundary between
 * customer input and data execution.
 *
 * <p><strong>This record's field set is closed. Adding a field is a security
 * review.</strong> The LLM fills it via {@code BeanOutputConverter}; every
 * value is then validated against the feed's declared parameter schema
 * before execution. No field here can express SQL, a table name, or an
 * arbitrary predicate.
 *
 * @param feedId     which feed to query
 * @param keywords   keyword filters (may be empty)
 * @param dateFrom   inclusive start date filter (nullable)
 * @param dateTo     inclusive end date filter (nullable)
 * @param states     state code filters (may be empty)
 * @param categories category filters (may be empty)
 * @param sortBy     sort field name (nullable)
 * @param limit      maximum rows to return (must be positive)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataQueryPlan(
        String feedId,
        List<String> keywords,
        LocalDate dateFrom,
        LocalDate dateTo,
        List<String> states,
        List<String> categories,
        String sortBy,
        int limit
) {
    public DataQueryPlan {
        if (limit <= 0) {
            throw new IllegalArgumentException("DataQueryPlan limit must be positive, got " + limit);
        }
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        states = states == null ? List.of() : List.copyOf(states);
        categories = categories == null ? List.of() : List.copyOf(categories);
    }
}
