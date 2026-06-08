package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * REST response DTO for the company discovery pipeline result.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
public record CompanyDiscoveryResponse(
        List<CompanyResponse> companies,
        String markdown,
        int totalScraped,
        int afterDedup,
        int aiHealthFiltered
) {
}
