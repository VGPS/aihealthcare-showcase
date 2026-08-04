package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link AnalystNoteEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface AnalystNoteRepository extends JpaRepository<AnalystNoteEntity, String> {

    List<AnalystNoteEntity> findByUserEmailOrderByUpdatedAtDesc(String userEmail);

    List<AnalystNoteEntity> findByUserEmailAndTargetTypeAndTargetIdOrderByUpdatedAtDesc(
            String userEmail, String targetType, String targetId);

    @Query("SELECT e FROM AnalystNoteEntity e WHERE e.userEmail = :email " +
           "AND COALESCE(e.updatedAt, e.createdAt) >= :since " +
           "ORDER BY COALESCE(e.updatedAt, e.createdAt) DESC")
    List<AnalystNoteEntity> findByUserEmailSince(
            @Param("email") String email, @Param("since") Instant since);

    void deleteByNoteIdAndUserEmail(String noteId, String userEmail);
}
