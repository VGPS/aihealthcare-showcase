package com.wgblackmon.aihealthcare.infrastructure.ai;

import java.util.List;

/**
 * Deserialization record for the Google Gemini REST API response.
 *
 * <p>Maps the JSON structure returned by
 * {@code POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent}.
 * Only the fields needed for text extraction are modelled; other response
 * metadata (usage stats, safety ratings) is ignored.
 *
 * <p>Nested records mirror the Gemini API JSON hierarchy:
 * {@code candidates[0].content.parts[0].text}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-14
 */
public record GeminiApiResponse(List<GeminiCandidate> candidates) {

    /**
     * A single candidate response from the model.
     */
    public record GeminiCandidate(GeminiContent content) {}

    /**
     * The content block containing generated parts.
     */
    public record GeminiContent(List<GeminiPart> parts) {}

    /**
     * A single part of the generated content (text-only).
     */
    public record GeminiPart(String text) {}
}
