package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing one outreach effort targeting a company.
 *
 * <p>One row per company per {@link OutreachPurpose} — a company can have separate
 * EMPLOYMENT and SUBSCRIPTION rows, each tracked independently. The {@code slug}
 * links to a {@code CompanyProfile} but lives in a separate table so pipeline
 * upserts on company_profiles never touch outreach state.
 *
 * @param id            surrogate primary key
 * @param slug          kebab-case company slug (FK to company_profiles)
 * @param purpose       EMPLOYMENT or SUBSCRIPTION
 * @param status        current lifecycle status
 * @param contactedAt   when this outreach first moved past NOT_STARTED; nullable
 * @param notes         free-form notes; nullable
 * @param createdAt     when the record was created
 * @param updatedAt     most recent modification timestamp
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
public record CompanyOutreach(
        Long id,
        String slug,
        OutreachPurpose purpose,
        OutreachStatus status,
        Instant contactedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public CompanyOutreach {
        if (slug == null || slug.isBlank()) throw new IllegalArgumentException("slug must not be blank");
        if (purpose == null)               throw new IllegalArgumentException("purpose must not be null");
        if (status == null)                throw new IllegalArgumentException("status must not be null");
        if (createdAt == null)             throw new IllegalArgumentException("createdAt must not be null");
        if (updatedAt == null)             throw new IllegalArgumentException("updatedAt must not be null");
    }
}
