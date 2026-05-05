package com.wgblackmon.aihealthcare.web.dto;

/**
 * Response DTO representing a single numbered citation within a {@link ResearchAnswerDto}.
 *
 * @param citationNumber  Sequential 1-based citation number.
 * @param title           Title of the cited source.
 * @param url             URL of the cited source.
 * @param retrievedAt     ISO-8601 timestamp string when the source was retrieved; may be null.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public record SourceCitationDto(
        int citationNumber,
        String title,
        String url,
        String retrievedAt
) {}
