package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a private analyst note attached
 * to an entity (company, article, regulatory event, clinical trial,
 * or wiki page).
 *
 * <p>Each note belongs to a single user and is identified by a UUID.
 * The {@code targetType} and {@code targetId} together identify the
 * annotated entity. The {@code targetLabel} is denormalized for display
 * so the central notes page does not need to resolve entity names.
 *
 * @param noteId      unique identifier (UUID)
 * @param userEmail   owner's email address (FK to app_users)
 * @param targetType  classification of the annotated entity
 * @param targetId    the slug, articleId, eventId, or trialId of the target
 * @param targetLabel human-readable label for display (e.g. company name, article title)
 * @param content     the note text (free-form, up to 5000 chars)
 * @param createdAt   when the note was created
 * @param updatedAt   when the note was last edited (null if never edited)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public record AnalystNote(
        String noteId,
        String userEmail,
        NoteTargetType targetType,
        String targetId,
        String targetLabel,
        String content,
        Instant createdAt,
        Instant updatedAt
) {

    /** Compact constructor — validates required fields. */
    public AnalystNote {
        if (noteId == null || noteId.isBlank()) {
            throw new IllegalArgumentException("noteId must not be blank");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("userEmail must not be blank");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType must not be null");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        if (targetLabel == null || targetLabel.isBlank()) {
            throw new IllegalArgumentException("targetLabel must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
