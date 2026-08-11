package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.GapItem;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the structured LLM response from the wiki gap analysis prompt
 * into domain records ({@link GapItem}) and an overall summary.
 *
 * <p>The parser splits the raw response by section headers
 * ({@code GAPS:}, {@code SUMMARY:}) and extracts field values from each
 * gap block ({@code TOPIC:}, {@code ARTICLE_IDS:}, {@code RECOMMENDATION:}).
 *
 * <p>Article IDs are validated against the set of IDs that were actually
 * sent in the prompt to catch LLM hallucinations. Invalid IDs are logged
 * at WARN level and discarded.
 *
 * <p>This class has no Spring dependencies and can be unit-tested
 * without a Spring context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
@Slf4j
public class WikiGapResponseParser {

    /**
     * Parses gap items from the LLM response.
     *
     * @param response         raw LLM response text
     * @param validArticleIds  set of article IDs that were in the prompt; used to filter hallucinated IDs
     * @return list of parsed gap items; empty if none found
     */
    public List<GapItem> parseGaps(String response, Set<String> validArticleIds) {
        log.debug("parseGaps() | responseLength={}, validArticleIds={}",
                response != null ? response.length() : 0,
                validArticleIds != null ? validArticleIds.size() : 0);

        List<GapItem> gaps = new ArrayList<>();
        if (response == null || response.isBlank()) {
            log.debug("parseGaps() | return=0 gaps");
            return gaps;
        }

        String gapsSection = extractSection(response, "GAPS:");
        if (gapsSection == null || gapsSection.isBlank()) {
            log.warn("parseGaps() | no GAPS: section found in response");
            log.debug("parseGaps() | return=0 gaps");
            return gaps;
        }

        String[] blocks = gapsSection.split("(?m)^(?=TOPIC:)");
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith("TOPIC:")) {
                continue;
            }

            String topic = extractField(trimmed, "TOPIC:");
            String articleIdsRaw = extractField(trimmed, "ARTICLE_IDS:");
            String recommendation = extractField(trimmed, "RECOMMENDATION:");

            if (topic == null || topic.isBlank()) {
                log.warn("parseGaps() | skipping block with blank TOPIC");
                continue;
            }
            if (recommendation == null || recommendation.isBlank()) {
                recommendation = "Create a new wiki page covering: " + topic;
            }

            List<String> articleIds = parseAndValidateIds(articleIdsRaw, validArticleIds);

            try {
                GapItem item = new GapItem(topic, articleIds, recommendation);
                gaps.add(item);
            } catch (IllegalArgumentException e) {
                log.warn("parseGaps() | skipping malformed gap block: {}", e.getMessage());
            }
        }

        log.debug("parseGaps() | return={} gaps", gaps.size());
        return gaps;
    }

    /**
     * Parses the overall summary from the LLM response.
     *
     * @param response raw LLM response text
     * @return the summary text, or a default message if not found
     */
    public String parseSummary(String response) {
        log.debug("parseSummary() | responseLength={}", response != null ? response.length() : 0);

        if (response == null || response.isBlank()) {
            String result = "No summary available.";
            log.debug("parseSummary() | return={}", result);
            return result;
        }

        String summary = extractSection(response, "SUMMARY:");
        if (summary == null || summary.isBlank()) {
            summary = "No summary available.";
        }

        log.debug("parseSummary() | return=summary (length={})", summary.length());
        return summary.trim();
    }

    private String extractSection(String response, String header) {
        int headerIdx = response.indexOf(header);
        if (headerIdx < 0) {
            return null;
        }
        String afterHeader = response.substring(headerIdx + header.length());

        String[] otherHeaders = {"GAPS:", "SUMMARY:"};
        int endIdx = afterHeader.length();
        for (String other : otherHeaders) {
            if (other.equals(header)) {
                continue;
            }
            int idx = afterHeader.indexOf(other);
            if (idx >= 0 && idx < endIdx) {
                endIdx = idx;
            }
        }
        return afterHeader.substring(0, endIdx).trim();
    }

    private String extractField(String block, String fieldName) {
        int fieldIdx = block.indexOf(fieldName);
        if (fieldIdx < 0) {
            return null;
        }
        String afterField = block.substring(fieldIdx + fieldName.length());
        int newlineIdx = afterField.indexOf('\n');
        if (newlineIdx >= 0) {
            return afterField.substring(0, newlineIdx).trim();
        }
        return afterField.trim();
    }

    private List<String> parseAndValidateIds(String raw, Set<String> validIds) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }

        String[] parts = raw.split("[,|]");
        Set<String> seen = new LinkedHashSet<>();
        for (String part : parts) {
            String id = part.trim();
            if (id.isEmpty()) {
                continue;
            }
            if (validIds != null && !validIds.isEmpty() && !validIds.contains(id)) {
                log.warn("parseAndValidateIds() | discarding hallucinated article ID: {}", id);
                continue;
            }
            if (seen.add(id)) {
                result.add(id);
            }
        }
        return result;
    }
}
