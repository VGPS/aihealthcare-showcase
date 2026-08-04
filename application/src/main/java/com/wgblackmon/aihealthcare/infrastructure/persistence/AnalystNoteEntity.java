package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code analyst_notes} table.
 *
 * <p>Maps to the immutable {@link com.wgblackmon.aihealthcare.domain.model.AnalystNote}
 * domain record. Conversion is performed inside {@link AnalystNoteAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Entity
@Table(name = "analyst_notes")
public class AnalystNoteEntity {

    @Id
    @Column(name = "note_id", nullable = false, length = 36)
    private String noteId;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 255)
    private String targetId;

    @Column(name = "target_label", nullable = false, length = 500)
    private String targetLabel;

    @Column(name = "content", nullable = false, length = 5000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Required no-arg constructor for JPA. */
    public AnalystNoteEntity() {}

    public String getNoteId()                           { return noteId; }
    public void setNoteId(String noteId)                { this.noteId = noteId; }

    public String getUserEmail()                        { return userEmail; }
    public void setUserEmail(String userEmail)          { this.userEmail = userEmail; }

    public String getTargetType()                       { return targetType; }
    public void setTargetType(String targetType)        { this.targetType = targetType; }

    public String getTargetId()                         { return targetId; }
    public void setTargetId(String targetId)            { this.targetId = targetId; }

    public String getTargetLabel()                      { return targetLabel; }
    public void setTargetLabel(String targetLabel)      { this.targetLabel = targetLabel; }

    public String getContent()                          { return content; }
    public void setContent(String content)              { this.content = content; }

    public Instant getCreatedAt()                       { return createdAt; }
    public void setCreatedAt(Instant createdAt)         { this.createdAt = createdAt; }

    public Instant getUpdatedAt()                       { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt)         { this.updatedAt = updatedAt; }
}
