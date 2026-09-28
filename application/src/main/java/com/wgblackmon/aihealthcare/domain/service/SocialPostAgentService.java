package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.SocialPostDraft;
import com.wgblackmon.aihealthcare.domain.port.inbound.DraftSocialPostUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SocialPostDraftPort;

import java.time.LocalDate;

/**
 * Implements the {@link DraftSocialPostUseCase} inbound port.
 *
 * <p>Pure domain service — no framework dependencies. Delegates entirely to
 * {@link SocialPostDraftPort}; the agent logic and tool-calling loop live in
 * the infrastructure adapter that implements that port.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-26
 * @updated 2026-09-26
 */
public class SocialPostAgentService implements DraftSocialPostUseCase {

    private final SocialPostDraftPort draftPort;

    public SocialPostAgentService(SocialPostDraftPort draftPort) {
        this.draftPort = draftPort;
    }

    @Override
    public SocialPostDraft draft(LocalDate date) {
        return draftPort.draft(date);
    }
}
