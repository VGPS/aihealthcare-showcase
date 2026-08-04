package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.AnalystNote;
import com.wgblackmon.aihealthcare.domain.model.NoteTargetType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link AnalystNote} records.
 *
 * <p>Notes are private per-user annotations on entities (companies,
 * articles, regulatory events, clinical trials, wiki pages).
 * Implementations live in the infrastructure layer (e.g. JPA adapter).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface AnalystNotePort {

    /**
     * Saves (inserts or updates) an analyst note.
     */
    void save(AnalystNote note);

    /**
     * Deletes an analyst note by ID, scoped to the owning user.
     */
    void delete(String noteId, String userEmail);

    /**
     * Returns all notes for a given user, ordered by updatedAt descending.
     */
    List<AnalystNote> findByUser(String email);

    /**
     * Returns notes for a specific target entity owned by the user,
     * ordered by updatedAt descending.
     */
    List<AnalystNote> findByUserAndTarget(String email, NoteTargetType targetType, String targetId);

    /**
     * Returns notes for a user where updatedAt (or createdAt if never edited)
     * is at or after the given timestamp, ordered by most recent first.
     *
     * @param email user email
     * @param since cutoff timestamp (inclusive)
     * @return notes modified since the given time
     */
    List<AnalystNote> findByUserSince(String email, Instant since);

    /**
     * Finds a single note by its ID.
     */
    Optional<AnalystNote> findById(String noteId);
}
