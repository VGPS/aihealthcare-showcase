package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code law_change_events} table.
 *
 * <p>Maps to the immutable {@link com.wgblackmon.aihealthcare.domain.model.LawChangeEvent}
 * domain record. Change events are created when the source monitor detects
 * content drift on a law's source URL. They remain unreviewed until an
 * admin acknowledges them. Conversion is performed inside {@link StateLawAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
@Entity
@Table(name = "law_change_events")
public class LawChangeEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "law_id", nullable = false, length = 100)
    private String lawId;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "change_type", nullable = false, length = 50)
    private String changeType;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "reviewed", nullable = false)
    private boolean reviewed;

    /** Required no-arg constructor for JPA. */
    public LawChangeEventEntity() {}

    public Long getId()                                     { return id; }
    public void setId(Long id)                              { this.id = id; }

    public String getLawId()                                { return lawId; }
    public void setLawId(String lawId)                      { this.lawId = lawId; }

    public Instant getDetectedAt()                          { return detectedAt; }
    public void setDetectedAt(Instant detectedAt)           { this.detectedAt = detectedAt; }

    public String getChangeType()                           { return changeType; }
    public void setChangeType(String changeType)            { this.changeType = changeType; }

    public String getDetail()                               { return detail; }
    public void setDetail(String detail)                    { this.detail = detail; }

    public boolean isReviewed()                             { return reviewed; }
    public void setReviewed(boolean reviewed)               { this.reviewed = reviewed; }
}
