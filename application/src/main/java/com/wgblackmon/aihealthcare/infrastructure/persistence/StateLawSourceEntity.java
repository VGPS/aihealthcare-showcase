package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code state_law_sources} table.
 *
 * <p>Maps to the immutable {@link com.wgblackmon.aihealthcare.domain.model.LawSource}
 * domain record. Each source is linked to a {@link StateLawEntity} via the
 * {@code lawId} column (a plain string FK, not a JPA relationship).
 * Conversion is performed inside {@link StateLawAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
@Entity
@Table(name = "state_law_sources")
public class StateLawSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "law_id", nullable = false, length = 100)
    private String lawId;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "last_fetched_at")
    private Instant lastFetchedAt;

    @Column(name = "last_content_hash", length = 64)
    private String lastContentHash;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "changed_since_last_review", nullable = false)
    private boolean changedSinceLastReview;

    /** Required no-arg constructor for JPA. */
    public StateLawSourceEntity() {}

    public Long getId()                                         { return id; }
    public void setId(Long id)                                  { this.id = id; }

    public String getLawId()                                    { return lawId; }
    public void setLawId(String lawId)                          { this.lawId = lawId; }

    public String getSourceType()                               { return sourceType; }
    public void setSourceType(String sourceType)                { this.sourceType = sourceType; }

    public String getUrl()                                      { return url; }
    public void setUrl(String url)                              { this.url = url; }

    public Instant getLastFetchedAt()                           { return lastFetchedAt; }
    public void setLastFetchedAt(Instant lastFetchedAt)         { this.lastFetchedAt = lastFetchedAt; }

    public String getLastContentHash()                          { return lastContentHash; }
    public void setLastContentHash(String lastContentHash)      { this.lastContentHash = lastContentHash; }

    public Integer getLastHttpStatus()                          { return lastHttpStatus; }
    public void setLastHttpStatus(Integer lastHttpStatus)       { this.lastHttpStatus = lastHttpStatus; }

    public boolean isChangedSinceLastReview()                   { return changedSinceLastReview; }
    public void setChangedSinceLastReview(boolean changed)      { this.changedSinceLastReview = changed; }
}
