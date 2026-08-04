package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classification of an analyst note's target entity — determines which
 * detail page the note is associated with.
 *
 * <ul>
 *   <li>{@code COMPANY} — note attached to a company profile (keyed by slug)</li>
 *   <li>{@code ARTICLE} — note attached to a news article (keyed by articleId)</li>
 *   <li>{@code REGULATORY_EVENT} — note attached to a regulatory event (keyed by eventId)</li>
 *   <li>{@code CLINICAL_TRIAL} — note attached to a clinical trial (keyed by trialId)</li>
 *   <li>{@code WIKI_PAGE} — note attached to a wiki page (keyed by slug)</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public enum NoteTargetType {
    COMPANY,
    ARTICLE,
    REGULATORY_EVENT,
    CLINICAL_TRIAL,
    WIKI_PAGE
}
