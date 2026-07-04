package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.model.WikiPageType;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the structured LLM response from the wiki compilation prompt
 * into domain records ({@link WikiPage}, {@link Contradiction}, warnings).
 *
 * <p>The parser splits the raw response by section headers
 * ({@code ### PAGE:}, {@code ### CONTRADICTION:}, {@code ### WARNING:})
 * and extracts field values from each section.  Malformed sections are
 * logged at WARN level and skipped — the compilation continues with
 * whatever was successfully parsed.
 *
 * <p>This class has no Spring dependencies and can be unit-tested
 * without a Spring context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Slf4j
public class WikiResponseParser {

    /**
     * Parses wiki page sections from the LLM response.
     *
     * @param response  raw LLM response text
     * @param createdAt timestamp to use for newly created pages
     * @return list of parsed wiki pages; empty if none found
     */
    public List<WikiPage> parsePages(String response, Instant createdAt) {
        log.debug("parsePages() | responseLength={}, createdAt={}",
                response != null ? response.length() : 0, createdAt);

        List<WikiPage> pages = new ArrayList<>();
        if (response == null || response.isBlank()) {
            log.debug("parsePages() | return=0 pages");
            return pages;
        }

        String[] sections = response.split("### PAGE:");
        for (int i = 1; i < sections.length; i++) {
            String section = sections[i];
            // Stop if we hit another section type
            int contradictionIdx = section.indexOf("### CONTRADICTION:");
            int warningIdx = section.indexOf("### WARNING:");
            int endIdx = section.length();
            if (contradictionIdx >= 0) {
                endIdx = Math.min(endIdx, contradictionIdx);
            }
            if (warningIdx >= 0) {
                endIdx = Math.min(endIdx, warningIdx);
            }
            section = section.substring(0, endIdx).trim();

            try {
                WikiPage page = parseSinglePage(section, createdAt);
                if (page != null) {
                    pages.add(page);
                }
            } catch (Exception e) {
                log.warn("parsePages() | skipping malformed PAGE section: {}", e.getMessage());
            }
        }

        log.debug("parsePages() | return={} pages", pages.size());
        return pages;
    }

    /**
     * Parses contradiction sections from the LLM response.
     *
     * @param response  raw LLM response text
     * @param detectedAt timestamp to use for detected contradictions
     * @return list of parsed contradictions; empty if none found
     */
    public List<Contradiction> parseContradictions(String response, Instant detectedAt) {
        log.debug("parseContradictions() | responseLength={}",
                response != null ? response.length() : 0);

        List<Contradiction> contradictions = new ArrayList<>();
        if (response == null || response.isBlank()) {
            log.debug("parseContradictions() | return=0 contradictions");
            return contradictions;
        }

        String[] sections = response.split("### CONTRADICTION:");
        for (int i = 1; i < sections.length; i++) {
            String section = sections[i];
            // Stop if we hit another section type
            int pageIdx = section.indexOf("### PAGE:");
            int warningIdx = section.indexOf("### WARNING:");
            int endIdx = section.length();
            if (pageIdx >= 0) {
                endIdx = Math.min(endIdx, pageIdx);
            }
            if (warningIdx >= 0) {
                endIdx = Math.min(endIdx, warningIdx);
            }
            section = section.substring(0, endIdx).trim();

            try {
                Contradiction contradiction = parseSingleContradiction(section, detectedAt);
                if (contradiction != null) {
                    contradictions.add(contradiction);
                }
            } catch (Exception e) {
                log.warn("parseContradictions() | skipping malformed CONTRADICTION section: {}",
                        e.getMessage());
            }
        }

        log.debug("parseContradictions() | return={} contradictions", contradictions.size());
        return contradictions;
    }

    /**
     * Parses warning messages from the LLM response.
     *
     * @param response raw LLM response text
     * @return list of warning strings; empty if none found
     */
    public List<String> parseWarnings(String response) {
        log.debug("parseWarnings() | responseLength={}",
                response != null ? response.length() : 0);

        List<String> warnings = new ArrayList<>();
        if (response == null || response.isBlank()) {
            log.debug("parseWarnings() | return=0 warnings");
            return warnings;
        }

        String[] sections = response.split("### WARNING:");
        for (int i = 1; i < sections.length; i++) {
            String section = sections[i];
            // Stop if we hit another section type
            int pageIdx = section.indexOf("### PAGE:");
            int contradictionIdx = section.indexOf("### CONTRADICTION:");
            int endIdx = section.length();
            if (pageIdx >= 0) {
                endIdx = Math.min(endIdx, pageIdx);
            }
            if (contradictionIdx >= 0) {
                endIdx = Math.min(endIdx, contradictionIdx);
            }
            String warning = section.substring(0, endIdx).trim();
            if (!warning.isEmpty()) {
                warnings.add(warning);
            }
        }

        log.debug("parseWarnings() | return={} warnings", warnings.size());
        return warnings;
    }

    private WikiPage parseSinglePage(String section, Instant createdAt) {
        log.debug("parseSinglePage() | sectionLength={}", section.length());

        String slug = extractFirstLineValue(section);
        String title = extractFieldValue(section, "TITLE:");
        String typeStr = extractFieldValue(section, "TYPE:");
        String tagsStr = extractFieldValue(section, "TAGS:");
        String sourcesStr = extractFieldValue(section, "SOURCES:");
        String relatedStr = extractFieldValue(section, "RELATED:");
        String content = extractContent(section);

        if (slug == null || slug.isBlank() || title == null || title.isBlank()) {
            log.warn("parseSinglePage() | missing slug or title, skipping");
            return null;
        }

        WikiPageType pageType;
        try {
            pageType = WikiPageType.valueOf(typeStr != null ? typeStr.trim() : "ENTITY");
        } catch (IllegalArgumentException e) {
            log.warn("parseSinglePage() | unknown page type '{}', defaulting to ENTITY", typeStr);
            pageType = WikiPageType.ENTITY;
        }

        List<String> tags = splitCommaDelimited(tagsStr);
        List<SourceRef> sources = expandArticleIds(sourcesStr);
        List<String> relatedSlugs = splitPipeDelimited(relatedStr);

        WikiPage result = new WikiPage(
                slug.trim(),
                title.trim(),
                pageType,
                tags,
                content != null ? content : "",
                sources,
                relatedSlugs,
                createdAt,
                null,
                1
        );

        log.debug("parseSinglePage() | return={}", result.slug());
        return result;
    }

    private Contradiction parseSingleContradiction(String section, Instant detectedAt) {
        log.debug("parseSingleContradiction() | sectionLength={}", section.length());

        String pageSlug = extractFirstLineValue(section);
        String priorClaim = extractFieldValue(section, "PRIOR_CLAIM:");
        String newClaim = extractFieldValue(section, "NEW_CLAIM:");
        String priorSourcesStr = extractFieldValue(section, "PRIOR_SOURCES:");
        String newSourcesStr = extractFieldValue(section, "NEW_SOURCES:");

        if (pageSlug == null || pageSlug.isBlank()
                || priorClaim == null || priorClaim.isBlank()
                || newClaim == null || newClaim.isBlank()) {
            log.warn("parseSingleContradiction() | missing required fields, skipping");
            return null;
        }

        List<SourceRef> priorSources = expandArticleIds(priorSourcesStr);
        List<SourceRef> newSources = expandArticleIds(newSourcesStr);

        Contradiction result = new Contradiction(
                pageSlug.trim(),
                priorClaim.trim(),
                newClaim.trim(),
                priorSources,
                newSources,
                detectedAt
        );

        log.debug("parseSingleContradiction() | return={}", result.pageSlug());
        return result;
    }

    /**
     * Extracts the value from the first line of a section (the text after the header split).
     * For example, from " fda-ai-guidance\nTITLE: ..." extracts "fda-ai-guidance".
     */
    private String extractFirstLineValue(String section) {
        String[] lines = section.split("\n");
        if (lines.length > 0) {
            return lines[0].trim();
        }
        return null;
    }

    /**
     * Extracts the value after a field label (e.g. "TITLE: FDA AI Guidance" → "FDA AI Guidance").
     */
    private String extractFieldValue(String section, String fieldLabel) {
        String[] lines = section.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(fieldLabel)) {
                return trimmed.substring(fieldLabel.length()).trim();
            }
        }
        return null;
    }

    /**
     * Extracts the multi-line content block after "CONTENT:" until end of section.
     */
    private String extractContent(String section) {
        int contentIdx = section.indexOf("CONTENT:");
        if (contentIdx < 0) {
            return "";
        }
        String afterContent = section.substring(contentIdx + "CONTENT:".length());
        return afterContent.trim();
    }

    private List<String> splitCommaDelimited(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        String[] parts = value.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<String> splitPipeDelimited(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        String[] parts = value.split("\\|");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Expands pipe-delimited article IDs into minimal {@link SourceRef} records.
     */
    private List<SourceRef> expandArticleIds(String pipeDelimited) {
        List<SourceRef> refs = new ArrayList<>();
        if (pipeDelimited == null || pipeDelimited.isBlank()) {
            return refs;
        }
        String[] ids = pipeDelimited.split("\\|");
        for (String id : ids) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                refs.add(new SourceRef(trimmed, "harvested", LocalDate.now(), null));
            }
        }
        return refs;
    }
}
