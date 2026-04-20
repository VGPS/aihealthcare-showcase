package com.wgblackmon.aihealthcare.web.dto;

/**
 * Response DTO for a competitor page harvest operation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
public record HarvestResultResponse(int pagesChecked, int changesDetected) {
}
