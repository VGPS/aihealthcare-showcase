package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity for the {@code regulatory_tracker} table.
 *
 * <p>Each row represents the current state of a single rulemaking/guidance action.
 * The {@code (docket_id, jurisdiction)} pair is a unique constraint used by the
 * upsert operation — existing rows are deleted and re-inserted on update.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Entity
@Table(
        name = "regulatory_tracker",
        uniqueConstraints = @UniqueConstraint(
                name = "uc_tracker_docket_jurisdiction",
                columnNames = {"docket_id", "jurisdiction"}
        )
)
public class RegulatoryTrackerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jurisdiction", nullable = false, length = 16)
    private String jurisdiction;

    @Column(nullable = false, length = 32)
    private String stage;

    @Column(name = "docket_id", nullable = false, length = 128)
    private String docketId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "comment_deadline")
    private LocalDate commentDeadline;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    protected RegulatoryTrackerEntity() {}

    public RegulatoryTrackerEntity(String jurisdiction, String stage, String docketId,
                                    String title, LocalDate commentDeadline,
                                    Instant lastUpdatedAt) {
        this.jurisdiction    = jurisdiction;
        this.stage           = stage;
        this.docketId        = docketId;
        this.title           = title;
        this.commentDeadline = commentDeadline;
        this.lastUpdatedAt   = lastUpdatedAt;
    }

    public Long getId()                   { return id; }
    public String getJurisdiction()       { return jurisdiction; }
    public String getStage()              { return stage; }
    public String getDocketId()           { return docketId; }
    public String getTitle()              { return title; }
    public LocalDate getCommentDeadline() { return commentDeadline; }
    public Instant getLastUpdatedAt()     { return lastUpdatedAt; }
}
