package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.SocialPostDraft;
import com.wgblackmon.aihealthcare.domain.port.outbound.SocialPostDraftPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SocialPostAgentService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-26
 * @updated 2026-09-26
 */
@ExtendWith(MockitoExtension.class)
class SocialPostAgentServiceTest {

    @Mock
    SocialPostDraftPort draftPort;

    @Test
    void draft_delegatesToPort() {
        LocalDate date = LocalDate.of(2026, 9, 26);
        SocialPostDraft expected = new SocialPostDraft(
                "LI body", "LI comment", "FB body", "FB comment",
                List.of("Headline A"), "rationale", Instant.now());
        when(draftPort.draft(date)).thenReturn(expected);

        SocialPostAgentService service = new SocialPostAgentService(draftPort);
        SocialPostDraft result = service.draft(date);

        assertThat(result).isSameAs(expected);
        verify(draftPort).draft(date);
    }

    @Test
    void draft_passesDateThroughUnmodified() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        SocialPostDraft stub = new SocialPostDraft(
                "LI body", "", "FB body", "", List.of(), "", Instant.now());
        when(draftPort.draft(date)).thenReturn(stub);

        new SocialPostAgentService(draftPort).draft(date);

        verify(draftPort).draft(date);
    }
}
