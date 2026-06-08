package com.wgblackmon.aihealthcare.web.dto;

/**
 * REST response DTO for a single discovered AI-healthcare company.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
public record CompanyResponse(
        String name,
        String source,
        String url,
        String companySite,
        String description,
        boolean scribe,
        boolean agent,
        boolean imaging,
        boolean rcm,
        boolean infra,
        boolean isAI,
        boolean isHealth
) {
}
