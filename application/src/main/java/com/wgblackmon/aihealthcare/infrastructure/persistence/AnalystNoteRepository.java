package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

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

    void deleteByNoteIdAndUserEmail(String noteId, String userEmail);
}
