package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code usage_records} table.
 *
 * <p>Tracks per-subscriber monthly AI query usage.  The composite primary key is
 * {@code (email, yearMonth)} — one row per subscriber per calendar month.
 *
 * <p>Mapping between this entity and the domain {@link com.wgblackmon.aihealthcare.domain.model.UsageRecord}
 * record is performed inside {@link UsageTrackingAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
@Entity
@Table(name = "usage_records")
@IdClass(UsageRecordId.class)
public class UsageRecordEntity {

    @Id
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Id
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "query_count", nullable = false)
    private int queryCount = 0;

    @Column(name = "query_limit", nullable = false)
    private int queryLimit = 0;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Required no-arg constructor for JPA. */
    public UsageRecordEntity() {}

    public String getEmail()                     { return email; }
    public void setEmail(String email)           { this.email = email; }

    public String getYearMonth()                 { return yearMonth; }
    public void setYearMonth(String yearMonth)   { this.yearMonth = yearMonth; }

    public int getQueryCount()                   { return queryCount; }
    public void setQueryCount(int queryCount)    { this.queryCount = queryCount; }

    public int getQueryLimit()                   { return queryLimit; }
    public void setQueryLimit(int queryLimit)    { this.queryLimit = queryLimit; }

    public Instant getUpdatedAt()                { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt)  { this.updatedAt = updatedAt; }
}
