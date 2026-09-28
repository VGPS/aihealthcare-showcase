package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.SocialPostDraft;

import java.time.LocalDate;

/**
 * Inbound port — triggers the agent-powered social post drafting pipeline.
 *
 * <p>The caller supplies a date; the agent autonomously decides which digest
 * entries to cover and how to frame them for LinkedIn and Facebook.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-26
 * @updated 2026-09-26
 */
public interface DraftSocialPostUseCase {

    /**
     * Drafts LinkedIn and Facebook posts sourced from the market digest for the given date.
     * Falls back to the most recent available digest if no digest exists for {@code date}.
     *
     * @param date the target digest date (non-null)
     * @return agent-authored draft with selection rationale
     */
    SocialPostDraft draft(LocalDate date);
}
