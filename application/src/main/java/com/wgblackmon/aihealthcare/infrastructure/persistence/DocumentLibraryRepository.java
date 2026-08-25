package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@code document_library} table.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-25
 * @updated 2026-08-25
 */
public interface DocumentLibraryRepository extends JpaRepository<DocumentLibraryEntity, String> {

    /** Returns all documents ordered by upload time descending (newest first). */
    List<DocumentLibraryEntity> findAllByOrderByUploadedAtDesc();
}
