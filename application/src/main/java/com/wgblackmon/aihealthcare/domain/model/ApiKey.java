package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing an API key for programmatic REST access.
 *
 * <p>Stores a SHA-256 hash of the actual key (never the raw key). The
 * {@code keyPrefix} (first 8 characters) is kept for display so users can
 * identify which key is which without exposing the full secret.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
public record ApiKey(
        String  id,
        String  ownerEmail,
        String  name,
        String  keyPrefix,
        String  keyHash,
        boolean active,
        Instant createdAt
) {
    public ApiKey {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("ownerEmail must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (keyHash == null || keyHash.isBlank()) {
            throw new IllegalArgumentException("keyHash must not be blank");
        }
    }
}
