package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code legal_trend_snapshots} table.
 *
 * <p>Stores a point-in-time legal trend detection snapshot. The rising trends
 * list is stored as a JSON-serialized CLOB and deserialized by
 * {@link LegalTrendSnapshotAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@Entity
@Table(name = "legal_trend_snapshots")
public class LegalTrendSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant generatedAt;

    private int windowDays;

    @Column(columnDefinition = "TEXT")
    private String risingTrendsJson;

    private int totalKeywords;

    /** Required no-arg constructor for JPA. */
    public LegalTrendSnapshotEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public int getWindowDays() { return windowDays; }
    public void setWindowDays(int windowDays) { this.windowDays = windowDays; }

    public String getRisingTrendsJson() { return risingTrendsJson; }
    public void setRisingTrendsJson(String risingTrendsJson) { this.risingTrendsJson = risingTrendsJson; }

    public int getTotalKeywords() { return totalKeywords; }
    public void setTotalKeywords(int totalKeywords) { this.totalKeywords = totalKeywords; }
}
