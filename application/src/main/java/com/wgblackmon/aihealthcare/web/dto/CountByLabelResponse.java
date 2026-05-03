package com.wgblackmon.aihealthcare.web.dto;

/**
 * Web DTO representing a single count-by-group entry in an analytics response.
 *
 * <p>Maps from {@link com.wgblackmon.aihealthcare.domain.model.CountByLabel}
 * for JSON serialization in analytics endpoints.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
public record CountByLabelResponse(String label, long count) { }
