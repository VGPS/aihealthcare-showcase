package com.wgblackmon.aihealthcare.web.dto;

import com.wgblackmon.aihealthcare.domain.model.CannedPrompt;
import com.wgblackmon.aihealthcare.domain.model.DataParameter;

import java.util.List;

/**
 * Response DTO for a canned enterprise data prompt.
 *
 * <p>Omits {@code templateText} — the raw prompt template is internal.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record CannedPromptResponse(
        String promptId,
        String label,
        String description,
        String feedId,
        List<DataParameter> parameters
) {

    public static CannedPromptResponse from(CannedPrompt prompt) {
        return new CannedPromptResponse(
                prompt.promptId(),
                prompt.label(),
                prompt.description(),
                prompt.feedId(),
                prompt.parameters());
    }
}
