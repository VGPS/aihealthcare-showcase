package com.wgblackmon.aihealthcare.domain.marketanalysis;

/**
 * AI-healthcare peer group for competitive clustering of affected companies.
 *
 * <p>Tagging each {@link AffectedCompany} with a {@code PeerGroup} enables a single
 * earnings beat (e.g. Doximity in {@code AI_SCRIBE_DOCUMENTATION}) to surface as a
 * read-through signal for private peers (Abridge, Nabla, Suki) that have no ticker
 * but belong in the digest narrative.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public enum PeerGroup {

    /** AI-powered clinical documentation and medical scribe platforms. */
    AI_SCRIBE_DOCUMENTATION,

    /** Value-based care enablement and population health management platforms. */
    VALUE_BASED_CARE_PLATFORM,

    /** AI-assisted radiology, pathology, and medical imaging analysis. */
    DIAGNOSTIC_IMAGING_AI,

    /** AI-driven drug discovery, molecular design, and clinical trial optimization. */
    DRUG_DISCOVERY_AI,

    /** Software-based therapeutic interventions delivered via digital channels. */
    DIGITAL_THERAPEUTICS,

    /** Company does not fit cleanly into another peer group. */
    OTHER
}
