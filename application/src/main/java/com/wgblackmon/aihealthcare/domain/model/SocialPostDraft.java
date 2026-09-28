package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Agent-drafted social media posts for LinkedIn and Facebook.
 *
 * <p>Produced by an LLM agent ({@link com.wgblackmon.aihealthcare.domain.port.outbound.SocialPostDraftPort})
 * that autonomously selects the most newsworthy market digest entries and
 * optionally fetches supporting article context via tool calls before drafting.
 * The {@code entriesSelected} and {@code rationale} fields expose the agent's
 * selection reasoning, enabling before/after comparison with the template-based output.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-26
 * @updated 2026-09-26
 */
public record SocialPostDraft(
        String linkedinBody,
        String linkedinComment,
        String facebookBody,
        String facebookComment,
        List<String> entriesSelected,
        String rationale,
        Instant generatedAt
) {
    public SocialPostDraft {
        if (linkedinBody == null || linkedinBody.isBlank()) {
            throw new IllegalArgumentException("linkedinBody must not be blank");
        }
        if (facebookBody == null || facebookBody.isBlank()) {
            throw new IllegalArgumentException("facebookBody must not be blank");
        }
        if (entriesSelected == null) {
            entriesSelected = List.of();
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt must not be null");
        }
    }
}
