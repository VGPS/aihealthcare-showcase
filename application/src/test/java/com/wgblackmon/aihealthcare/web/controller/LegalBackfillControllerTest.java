package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.legal.LegalBackfillService;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.legal.LegalBackfillService.BackfillResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link LegalBackfillController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(LegalBackfillController.class)
class LegalBackfillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyPort apiKeyPort;

    @MockBean
    private LegalBackfillService legalBackfillService;

    @Test
    void triggerBackfill_returns200() throws Exception {
        when(legalBackfillService.runBackfill(1095))
                .thenReturn(new BackfillResult(5, 10, 3, List.of()));

        mockMvc.perform(post("/monitoring/legal-backfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Backfill completed"))
                .andExpect(jsonPath("$.courtListenerCount").value(5))
                .andExpect(jsonPath("$.pubmedCount").value(10))
                .andExpect(jsonPath("$.regulatoryCount").value(3))
                .andExpect(jsonPath("$.totalCount").value(18));
    }

    @Test
    void triggerBackfill_defaultDays1095() throws Exception {
        when(legalBackfillService.runBackfill(1095))
                .thenReturn(new BackfillResult(0, 0, 0, List.of()));

        mockMvc.perform(post("/monitoring/legal-backfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookbackDays").value(1095));

        verify(legalBackfillService).runBackfill(1095);
    }

    @Test
    void triggerBackfill_customDaysRespected() throws Exception {
        when(legalBackfillService.runBackfill(365))
                .thenReturn(new BackfillResult(2, 5, 1, List.of()));

        mockMvc.perform(post("/monitoring/legal-backfill?days=365"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookbackDays").value(365));

        verify(legalBackfillService).runBackfill(365);
    }

    @Test
    void triggerBackfill_reportsErrors() throws Exception {
        when(legalBackfillService.runBackfill(1095))
                .thenReturn(new BackfillResult(0, 5, 0, List.of("CourtListener failed: timeout")));

        mockMvc.perform(post("/monitoring/legal-backfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0]").value("CourtListener failed: timeout"))
                .andExpect(jsonPath("$.pubmedCount").value(5));
    }
}
