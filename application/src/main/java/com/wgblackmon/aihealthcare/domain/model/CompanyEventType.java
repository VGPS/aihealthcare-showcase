package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classification of events detected in articles linked to a company profile.
 *
 * <p>Used by {@link com.wgblackmon.aihealthcare.domain.service.CompanyProfileService}
 * to categorize events extracted from article titles and body text via
 * keyword-based heuristics.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public enum CompanyEventType {

    /** Funding round, investment, or acquisition. */
    FUNDING,

    /** Product launch, new feature, or platform update. */
    PRODUCT_LAUNCH,

    /** FDA clearance, CMS decision, or regulatory filing. */
    REGULATORY,

    /** Strategic partnership, collaboration, or integration. */
    PARTNERSHIP,

    /** Events that do not match other categories. */
    OTHER
}
