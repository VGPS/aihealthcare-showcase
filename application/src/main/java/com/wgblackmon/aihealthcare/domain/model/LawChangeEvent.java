package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Records a detected change in a law source URL's content.
 *
 * <p>When the source monitor re-fetches a law's source URL and the content
 * hash differs from the last known hash, a change event is created. Events
 * remain unreviewed until an admin inspects and acknowledges them.
 *
 * @param id          database-assigned identifier (nullable for new events)
 * @param lawId       the slug identifier of the affected {@link StateLaw}
 * @param detectedAt  when the change was detected
 * @param changeType  type of change (e.g. "CONTENT_CHANGED", "URL_UNAVAILABLE")
 * @param detail      human-readable description of the detected change
 * @param reviewed    whether an admin has reviewed this event
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public record LawChangeEvent(
        Long id,
        String lawId,
        Instant detectedAt,
        String changeType,
        String detail,
        boolean reviewed
) {

    /**
     * Compact constructor — validates required fields.
     */
    public LawChangeEvent {
        if (lawId == null || lawId.isBlank()) {
            throw new IllegalArgumentException("lawId must not be blank");
        }
    }
}
