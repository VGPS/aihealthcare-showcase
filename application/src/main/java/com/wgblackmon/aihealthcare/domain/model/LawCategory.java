package com.wgblackmon.aihealthcare.domain.model;

import java.util.Optional;

/**
 * Classification of state health-AI legislation by subject area.
 *
 * <p>A single law may span multiple categories (e.g. a comprehensive AI act
 * that also covers payer utilization review). The categories are used for
 * filtering and grouping in the legislation registry UI.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public enum LawCategory {

    /** Laws regulating AI in payer prior-authorization and utilization review. */
    PAYER_UTILIZATION_REVIEW("Payer Utilization Review"),

    /** Laws addressing AI-driven claims downcoding or denial patterns. */
    CLAIMS_DOWNCODING("Claims Downcoding"),

    /** Laws governing provider use of AI in clinical decision-making. */
    PROVIDER_CLINICAL_USE("Provider Clinical Use"),

    /** Laws requiring disclosure or consent when AI is used in care. */
    PROVIDER_DISCLOSURE_CONSENT("Provider Disclosure & Consent"),

    /** Laws specific to AI in mental health or psychotherapy contexts. */
    MENTAL_HEALTH_PSYCHOTHERAPY("Mental Health & Psychotherapy"),

    /** Laws regulating consumer-facing health chatbots and virtual assistants. */
    CONSUMER_CHATBOTS("Consumer Chatbots"),

    /** Broad AI governance acts covering multiple healthcare AI use cases. */
    COMPREHENSIVE_AI_ACT("Comprehensive AI Act"),

    /** Laws that do not fit the other defined categories. */
    OTHER("Other");

    private final String displayLabel;

    LawCategory(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    /**
     * Returns the human-readable label for UI display.
     */
    public String displayLabel() {
        return displayLabel;
    }

    /**
     * Parses a category code string (case-insensitive) into a {@code LawCategory}.
     *
     * @param code the enum name (e.g. "PAYER_UTILIZATION_REVIEW")
     * @return the matching {@code LawCategory}, or empty if not found
     */
    public static Optional<LawCategory> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String upper = code.trim().toUpperCase();
        for (LawCategory cat : values()) {
            if (cat.name().equals(upper)) {
                return Optional.of(cat);
            }
        }
        return Optional.empty();
    }
}
