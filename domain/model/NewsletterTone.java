package com.wgblackmon.aihealthcare.domain.model;

/**
 * Writing tone applied to AI-generated newsletter content.
 *
 * <ul>
 *   <li>{@link #PROFESSIONAL} – Formal and clinical; suited for healthcare executives.</li>
 *   <li>{@link #ACCESSIBLE}   – Plain language; suited for a general healthcare audience.</li>
 *   <li>{@link #TECHNICAL}    – Detail-oriented; suited for engineers and data scientists.</li>
 * </ul>
 */
public enum NewsletterTone {
    PROFESSIONAL,
    ACCESSIBLE,
    TECHNICAL
}
