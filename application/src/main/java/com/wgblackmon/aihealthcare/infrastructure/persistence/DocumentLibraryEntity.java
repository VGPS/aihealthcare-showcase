package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code document_library} table.
 *
 * <p>Maps to the immutable {@link com.wgblackmon.aihealthcare.domain.model.DocumentRecord}
 * domain record. Conversion is performed inside {@link DocumentLibraryAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-25
 * @updated 2026-08-25
 */
@Entity
@Table(name = "document_library")
public class DocumentLibraryEntity {

    @Id
    @Column(name = "doc_id", nullable = false, length = 36)
    private String docId;

    @Column(name = "filename", nullable = false, length = 500)
    private String filename;

    @Column(name = "source_label", nullable = false, length = 200)
    private String sourceLabel;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "wiki_page_slug", length = 300)
    private String wikiPageSlug;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Required no-arg constructor for JPA. */
    public DocumentLibraryEntity() {}

    public String getDocId()                          { return docId; }
    public void setDocId(String docId)                { this.docId = docId; }

    public String getFilename()                       { return filename; }
    public void setFilename(String filename)          { this.filename = filename; }

    public String getSourceLabel()                    { return sourceLabel; }
    public void setSourceLabel(String sourceLabel)    { this.sourceLabel = sourceLabel; }

    public Instant getUploadedAt()                    { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt)     { this.uploadedAt = uploadedAt; }

    public int getChunkCount()                        { return chunkCount; }
    public void setChunkCount(int chunkCount)         { this.chunkCount = chunkCount; }

    public String getWikiPageSlug()                   { return wikiPageSlug; }
    public void setWikiPageSlug(String wikiPageSlug)  { this.wikiPageSlug = wikiPageSlug; }

    public String getStatus()                         { return status; }
    public void setStatus(String status)              { this.status = status; }

    public String getErrorMessage()                   { return errorMessage; }
    public void setErrorMessage(String errorMessage)  { this.errorMessage = errorMessage; }
}
