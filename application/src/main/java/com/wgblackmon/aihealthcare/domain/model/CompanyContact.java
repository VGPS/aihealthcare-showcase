package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing an individual contact at a company.
 *
 * <p>Contacts are linked to the company (via slug), not to a specific outreach
 * purpose, so one person record is shared across EMPLOYMENT and SUBSCRIPTION
 * outreach for the same company.  Discovered primarily via LinkedIn.
 *
 * @param id          surrogate primary key
 * @param slug        kebab-case company slug (FK to company_profiles)
 * @param fullName    contact's full name
 * @param jobTitle    job title; nullable
 * @param email       work email; nullable
 * @param linkedinUrl profile URL; nullable
 * @param source      how this contact was found
 * @param status      current outreach status for this individual
 * @param notes       free-form notes; nullable
 * @param createdAt   when the record was created
 * @param updatedAt   most recent modification timestamp
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
public record CompanyContact(
        Long id,
        String slug,
        String fullName,
        String jobTitle,
        String email,
        String linkedinUrl,
        ContactSource source,
        ContactStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public CompanyContact {
        if (slug == null || slug.isBlank())         throw new IllegalArgumentException("slug must not be blank");
        if (fullName == null || fullName.isBlank())  throw new IllegalArgumentException("fullName must not be blank");
        if (source == null)                          throw new IllegalArgumentException("source must not be null");
        if (status == null)                          throw new IllegalArgumentException("status must not be null");
        if (createdAt == null)                       throw new IllegalArgumentException("createdAt must not be null");
        if (updatedAt == null)                       throw new IllegalArgumentException("updatedAt must not be null");
    }
}
