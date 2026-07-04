package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;

/**
 * Response DTO for API key operations.
 *
 * <p>The {@code rawKey} field is only populated on creation (POST). Subsequent
 * list/get operations return {@code null} for {@code rawKey} — the raw key is
 * never stored and cannot be retrieved after creation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
public record ApiKeyResponse(
        String  id,
        String  name,
        String  keyPrefix,
        String  rawKey,
        boolean active,
        Instant createdAt
) {}
