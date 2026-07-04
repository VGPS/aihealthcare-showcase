package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * JPA entity for the {@code api_keys} table.
 *
 * <p>Stores a SHA-256 hash of the raw API key. The raw key is never persisted;
 * only the first 8 characters ({@code keyPrefix}) are kept for display.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
@Slf4j
@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at")
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
