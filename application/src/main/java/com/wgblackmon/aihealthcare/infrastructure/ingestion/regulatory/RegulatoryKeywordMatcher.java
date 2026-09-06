package com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Word-boundary keyword matcher for regulatory event filtering.
 *
 * <p>Uses {@code \b} regex word boundaries so short keywords like "AI"
 * match "AI-powered imaging" but not "brain" or "strain". Replaces
 * the previous {@code String.contains()} approach which could not
 * safely handle short keywords.
 *
 * <p>Shared by {@link Fda510kHarvester}, {@link FdaDeNovoHarvester},
 * and {@link CmsRuleHarvester}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
final class RegulatoryKeywordMatcher {

    private RegulatoryKeywordMatcher() {}

    /**
     * Returns the subset of {@code aiKeywords} that appear in {@code text}
     * at word boundaries (case-insensitive).
     */
    static List<String> matchKeywords(String text, List<String> aiKeywords) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> matched = new ArrayList<>();
        for (String keyword : aiKeywords) {
            Pattern pattern = Pattern.compile(
                    "\\b" + Pattern.quote(keyword) + "\\b",
                    Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(text).find()) {
                matched.add(keyword);
            }
        }
        return matched;
    }
}
