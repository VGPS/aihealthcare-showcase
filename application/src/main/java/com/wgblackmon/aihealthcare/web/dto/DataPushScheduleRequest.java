package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating or updating a push schedule.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataPushScheduleRequest(
        String label,
        String feedId,
        String promptId,
        String promptText,
        Map<String, String> parameters,
        String format,
        String cronExpression,
        String zoneId,
        List<String> recipients,
        Boolean active
) {
}
