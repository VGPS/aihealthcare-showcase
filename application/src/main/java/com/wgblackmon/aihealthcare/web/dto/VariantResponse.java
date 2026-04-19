package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;

/**
 * Response DTO for a prompt variant.
 *
 * @param variantId    unique identifier for the variant
 * @param name         human-readable label
 * @param templateText the full prompt template with placeholders
 * @param description  free-text description
 * @param createdAt    timestamp when the variant was created
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
public record VariantResponse(
        String variantId,
        String name,
        String templateText,
        String description,
        Instant createdAt
) {}
