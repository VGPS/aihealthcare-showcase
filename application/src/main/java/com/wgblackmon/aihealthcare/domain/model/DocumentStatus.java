package com.wgblackmon.aihealthcare.domain.model;

/**
 * Lifecycle state of a document uploaded through the Document Library.
 *
 * <p>Transitions in order: {@code UPLOADED → INDEXED → WIKI_COMPILED}.
 * A failed step sets the status to {@code FAILED} without advancing further.
 * {@code INDEXED} is a valid terminal state when wiki compilation fails but
 * the vector store write succeeded.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-25
 * @updated 2026-08-25
 */
public enum DocumentStatus {
    /** File received and saved to disk; ingestion not yet started. */
    UPLOADED,
    /** Chunks embedded in the vector store; wiki compilation not yet run. */
    INDEXED,
    /** Wiki page created from this document; fully integrated. */
    WIKI_COMPILED,
    /** Processing failed — see the associated error message. */
    FAILED
}
