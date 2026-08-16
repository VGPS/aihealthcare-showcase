package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code health_systems} table.
 *
 * <p>Master entity for the AI Accountability Tracker. The {@code id} is a
 * kebab-case slug (e.g. "kaiser", "hca"). {@code aliasesPipe} stores
 * historical entity names pipe-delimited for disambiguation.
 *
 * @author  Bill Blackmon
 * @since   2026-08-16
 * @updated 2026-08-16
 */
@Entity
@Table(name = "health_systems")
public class HealthSystemEntity {

    @Id
    private String id;

    private String canonicalName;

    @Column(columnDefinition = "TEXT")
    private String aliasesPipe;

    private String hqState;

    private String systemType;

    private Instant createdAt;

    /** Required no-arg constructor for JPA. */
    public HealthSystemEntity() {}

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCanonicalName() { return canonicalName; }
    public void setCanonicalName(String canonicalName) { this.canonicalName = canonicalName; }

    public String getAliasesPipe() { return aliasesPipe; }
    public void setAliasesPipe(String aliasesPipe) { this.aliasesPipe = aliasesPipe; }

    public String getHqState() { return hqState; }
    public void setHqState(String hqState) { this.hqState = hqState; }

    public String getSystemType() { return systemType; }
    public void setSystemType(String systemType) { this.systemType = systemType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
