package com.wgblackmon.aihealthcare.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * An item in the healthcare AI editorial calendar.
 *
 * <p>Tracks the full writing lifecycle — from initial planning through
 * research, drafting, review, scheduling, and publication — for a single
 * healthcare AI article. The {@code id} is a kebab-case slug that doubles
 * as the stem of the published {@code /insights/{slug}.html} filename.
 *
 * <p>Only healthcare-AI-focused items are included. Consumer personal-finance
 * topics (credit scores, 401k basics, BNPL, student loans) are out of scope
 * and were intentionally excluded from the seed dataset.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public record EditorialItem(
        String id,
        String title,
        String hook,
        EditorialTheme theme,
        EditorialDemandSignal demandSignal,
        EditorialPriority priorityTier,
        EditorialEffort effort,
        String format,
        LocalDate publishWindowStart,
        LocalDate publishWindowEnd,
        LocalDate preferredDate,
        String cta,
        EditorialStatus status,
        LocalDate lastVerified,
        List<String> audiences,
        List<EditorialSource> primarySources) {

    public EditorialItem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (theme == null) throw new IllegalArgumentException("theme must not be null");
        if (priorityTier == null) throw new IllegalArgumentException("priorityTier must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (preferredDate == null) throw new IllegalArgumentException("preferredDate must not be null");
        audiences = audiences == null ? List.of() : List.copyOf(audiences);
        primarySources = primarySources == null ? List.of() : List.copyOf(primarySources);
    }
}
