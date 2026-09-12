package com.wgblackmon.aihealthcare.domain.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared utility for converting between pipe-delimited strings and lists.
 *
 * <p>Many persistence adapters store list fields (categories, article IDs,
 * keywords, URLs, recipients) as pipe-delimited strings in a single TEXT
 * column. This utility centralizes the split/join logic that was previously
 * duplicated across 14+ adapter classes.
 *
 * <p>This class lives in the {@code domain.service} package and uses
 * only JDK types — no Spring, no Lombok.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-12
 * @updated 2026-09-12
 */
public final class PipeDelimitedUtils {

    private PipeDelimitedUtils() {}

    /**
     * Splits a pipe-delimited string into a list of trimmed, non-empty elements.
     *
     * @param pipeDelimited the pipe-delimited string (may be null or blank)
     * @return an unmodifiable list of trimmed elements; empty list if input is null/blank
     */
    public static List<String> split(String pipeDelimited) {
        if (pipeDelimited == null || pipeDelimited.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        String[] parts = pipeDelimited.split("\\|");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Joins a list of strings into a pipe-delimited string.
     *
     * @param items the list of strings (may be null)
     * @return pipe-delimited string; empty string if input is null or empty
     */
    public static String join(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return String.join("|", items);
    }
}
