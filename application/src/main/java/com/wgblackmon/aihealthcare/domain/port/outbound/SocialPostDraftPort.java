package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.SocialPostDraft;

import java.time.LocalDate;

/**
 * Outbound port — LLM agent that drafts platform-optimized social media posts.
 *
 * <p>The adapter implementing this port is responsible for the full agent loop:
 * tool call registration, iterative tool execution, and response parsing.
 * No pre-fetched data is passed in — the agent retrieves what it needs via tools.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-26
 * @updated 2026-09-26
 */
public interface SocialPostDraftPort {

    /**
     * Runs the agent drafting loop for the given date.
     *
     * @param date the target digest date (non-null)
     * @return drafted posts with agent selection metadata
     */
    SocialPostDraft draft(LocalDate date);
}
