package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code trend_snapshots} table.
 *
 * <p>Stores a point-in-time trend detection snapshot. The rising, fading,
 * and new topic lists are stored as JSON-serialized CLOBs and deserialized
 * by {@link TrendSnapshotAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Entity
@Table(name = "trend_snapshots")
public class TrendSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant generatedAt;

    private int windowDays;

    @Column(columnDefinition = "TEXT")
    private String risingJson;

    @Column(columnDefinition = "TEXT")
    private String fadingJson;

    @Column(columnDefinition = "TEXT")
    private String newJson;

    private int totalKeywords;

    /** Required no-arg constructor for JPA. */
    public TrendSnapshotEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public int getWindowDays() { return windowDays; }
    public void setWindowDays(int windowDays) { this.windowDays = windowDays; }

    public String getRisingJson() { return risingJson; }
    public void setRisingJson(String risingJson) { this.risingJson = risingJson; }

    public String getFadingJson() { return fadingJson; }
    public void setFadingJson(String fadingJson) { this.fadingJson = fadingJson; }

    public String getNewJson() { return newJson; }
    public void setNewJson(String newJson) { this.newJson = newJson; }

    public int getTotalKeywords() { return totalKeywords; }
    public void setTotalKeywords(int totalKeywords) { this.totalKeywords = totalKeywords; }
}
