package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link AnalystNote} domain record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
class AnalystNoteTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    @Test
    void validNote_constructsSuccessfully() {
        AnalystNote note = new AnalystNote(
                "note-1", "user@test.com", NoteTargetType.COMPANY,
                "tempus-ai", "Tempus AI", "Great company for genomics",
                NOW, NOW);

        assertThat(note.noteId()).isEqualTo("note-1");
        assertThat(note.userEmail()).isEqualTo("user@test.com");
        assertThat(note.targetType()).isEqualTo(NoteTargetType.COMPANY);
        assertThat(note.targetId()).isEqualTo("tempus-ai");
        assertThat(note.targetLabel()).isEqualTo("Tempus AI");
        assertThat(note.content()).isEqualTo("Great company for genomics");
        assertThat(note.createdAt()).isEqualTo(NOW);
        assertThat(note.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void nullNoteId_throwsException() {
        assertThatThrownBy(() -> new AnalystNote(
                null, "user@test.com", NoteTargetType.COMPANY,
                "tempus-ai", "Tempus AI", "content", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("noteId");
    }

    @Test
    void blankContent_throwsException() {
        assertThatThrownBy(() -> new AnalystNote(
                "note-1", "user@test.com", NoteTargetType.ARTICLE,
                "art-123", "Some Article", "  ", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void nullTargetType_throwsException() {
        assertThatThrownBy(() -> new AnalystNote(
                "note-1", "user@test.com", null,
                "art-123", "Some Article", "content", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetType");
    }

    @Test
    void nullCreatedAt_throwsException() {
        assertThatThrownBy(() -> new AnalystNote(
                "note-1", "user@test.com", NoteTargetType.WIKI_PAGE,
                "ai-healthcare", "AI Healthcare", "content", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void nullUpdatedAt_acceptedOnConstruction() {
        AnalystNote note = new AnalystNote(
                "note-1", "user@test.com", NoteTargetType.CLINICAL_TRIAL,
                "trial-123", "Phase 3 Trial", "Promising results",
                NOW, null);

        assertThat(note.updatedAt()).isNull();
    }
}
