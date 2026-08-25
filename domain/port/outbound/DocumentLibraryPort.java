package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DocumentRecord;
import com.wgblackmon.aihealthcare.domain.model.DocumentStatus;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link DocumentRecord} entries
 * that track files uploaded through the Document Library.
 *
 * <p>Adapters implementing this port live in
 * {@code infrastructure.persistence} and use JPA for storage.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-25
 * @updated 2026-08-25
 */
public interface DocumentLibraryPort {

    /** Persists a new document record. */
    void save(DocumentRecord record);

    /** Returns all document records, most recently uploaded first. */
    List<DocumentRecord> findAll();

    /** Returns the document record with the given ID, or empty if not found. */
    Optional<DocumentRecord> findById(String docId);

    /**
     * Updates the status, chunk count, wiki page slug, and error message
     * for the given document.
     *
     * @param docId        the document to update
     * @param status       new lifecycle state
     * @param chunkCount   number of chunks embedded (ignored when status is FAILED)
     * @param wikiPageSlug slug of the compiled wiki page; may be null
     * @param errorMessage failure description; null when status is not FAILED
     */
    void updateStatus(String docId, DocumentStatus status,
                      int chunkCount, String wikiPageSlug, String errorMessage);
}
