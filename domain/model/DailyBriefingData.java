package com.wgblackmon.aihealthcare.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable domain record holding all assembled data for one subscriber's
 * personalized daily briefing email.
 *
 * <p>This record is the input to {@code DailyBriefingRenderer}, which
 * produces HTML and plain-text email content from its fields.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public record DailyBriefingData(
        String subscriberName,
        String subscriberEmail,
        LocalDate briefingDate,
        List<WatchlistMatch> recentMatches,
        List<CompanySentiment> watchedCompanySentiments,
        List<AnalystNote> recentNotes
) {
    public DailyBriefingData {
        if (subscriberEmail == null || subscriberEmail.isBlank()) {
            throw new IllegalArgumentException("subscriberEmail must not be blank");
        }
        if (briefingDate == null) {
            throw new IllegalArgumentException("briefingDate must not be null");
        }
        if (recentMatches == null) {
            throw new IllegalArgumentException("recentMatches must not be null");
        }
        if (watchedCompanySentiments == null) {
            throw new IllegalArgumentException("watchedCompanySentiments must not be null");
        }
        if (recentNotes == null) {
            throw new IllegalArgumentException("recentNotes must not be null");
        }
        recentMatches = List.copyOf(recentMatches);
        watchedCompanySentiments = List.copyOf(watchedCompanySentiments);
        recentNotes = List.copyOf(recentNotes);
    }
}
