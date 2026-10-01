package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.LawCategory;

import java.util.Map;

/**
 * Static keyword hints used to discover "related content" cross-links between
 * state laws, companies, and wiki pages (T9 internal-linking SEO work).
 *
 * <p>Deliberately coarse — a best-effort discovery aid for spreading crawl
 * across sections, not a scored relevance engine. {@link LawCategory} values
 * use a controlled vocabulary that rarely appears verbatim in free-text company
 * descriptions or wiki content, so each category maps to a plain-language
 * phrase more likely to match.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-30
 * @updated 2026-09-30
 */
final class RelatedContentKeywords {

    private static final Map<LawCategory, String> LAW_CATEGORY_KEYWORD = Map.ofEntries(
            Map.entry(LawCategory.PAYER_UTILIZATION_REVIEW, "utilization review"),
            Map.entry(LawCategory.CLAIMS_DOWNCODING, "claims"),
            Map.entry(LawCategory.PROVIDER_CLINICAL_USE, "clinical decision"),
            Map.entry(LawCategory.PROVIDER_DISCLOSURE_CONSENT, "disclosure"),
            Map.entry(LawCategory.MENTAL_HEALTH_PSYCHOTHERAPY, "mental health"),
            Map.entry(LawCategory.CONSUMER_CHATBOTS, "chatbot"),
            Map.entry(LawCategory.COMPREHENSIVE_AI_ACT, "artificial intelligence"),
            Map.entry(LawCategory.OTHER, "healthcare ai")
    );

    private RelatedContentKeywords() {
    }

    /** The search phrase used to find wiki pages / companies related to a law category. */
    static String forLawCategory(LawCategory category) {
        return LAW_CATEGORY_KEYWORD.getOrDefault(category, "healthcare ai");
    }
}
