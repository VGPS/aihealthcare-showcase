package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * REST response DTO representing a single model's AI synthesis of search results.
 *
 * <p>Mapped from the domain {@link com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis}
 * record. Used as a nested element within {@link AiSearchResponse}.
 *
 * @param modelName   human-readable model name (e.g. "Claude", "GPT")
 * @param summary     AI-generated synthesis paragraph
 * @param keyFindings extracted key findings as bullet points
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-06-02
 */
public record AiSearchSynthesisDto(
        String modelName,
        String summary,
        List<String> keyFindings
) {
}
