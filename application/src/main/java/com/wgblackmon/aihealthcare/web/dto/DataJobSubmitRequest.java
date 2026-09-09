package com.wgblackmon.aihealthcare.web.dto;

import java.util.Map;

/**
 * Request body for submitting an enterprise data job.
 *
 * <p>Exactly one of {@code promptId}, {@code promptText}, or bare
 * {@code parameters} must be supplied. The controller maps this DTO
 * into the domain {@code DataRequest} before delegating to the use case.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataJobSubmitRequest(
        String feedId,
        String promptId,
        String promptText,
        Map<String, String> parameters,
        String format,
        Integer rowLimit,
        String connectionId
) {}
