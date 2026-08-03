package com.wgblackmon.aihealthcare.web.dto;

/**
 * Lightweight DTO summarising a single trend snapshot for the history timeline
 * table.  Avoids passing full signal lists to the Thymeleaf template.
 *
 * @param epochMillis  snapshot timestamp as epoch milliseconds (used in detail page URL)
 * @param generatedAt  formatted display string (e.g. "Aug 3, 2026 8:00 AM EDT")
 * @param risingCount  number of rising-topic signals in the snapshot
 * @param newCount     number of new-topic signals in the snapshot
 * @param totalKeywords total unique keywords analysed in this snapshot
 * @param windowDays   primary analysis window (typically 30)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public record TrendSnapshotSummary(
        long epochMillis,
        String generatedAt,
        int risingCount,
        int newCount,
        int totalKeywords,
        int windowDays
) {}
