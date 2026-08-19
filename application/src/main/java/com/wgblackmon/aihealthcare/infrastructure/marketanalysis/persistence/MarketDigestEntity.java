package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity for the {@code market_digest} table — one row per digest date.
 *
 * <p>The {@code digest_date} column has a unique constraint (enforced in the domain
 * by the adapter's upsert logic: delete-then-insert when saving for a date that already exists).
 *
 * <p>Child entries are stored in {@link MarketDigestEntryEntity} and queried separately
 * by {@code digest_id} — no {@code @OneToMany} is used, following the project convention.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Entity
@Table(name = "market_digest")
public class MarketDigestEntity {

    @Id
    @Column(name = "digest_id", nullable = false, length = 36)
    private String digestId;

    @Column(name = "digest_date", nullable = false)
    private LocalDate digestDate;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected MarketDigestEntity() {}

    public MarketDigestEntity(String digestId, LocalDate digestDate, Instant generatedAt) {
        this.digestId = digestId;
        this.digestDate = digestDate;
        this.generatedAt = generatedAt;
    }

    public String getDigestId() { return digestId; }
    public LocalDate getDigestDate() { return digestDate; }
    public Instant getGeneratedAt() { return generatedAt; }
}
