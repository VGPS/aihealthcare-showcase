package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.legal.LegalBackfillService;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.legal.LegalBackfillService.BackfillResult;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineAsyncRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * @updated 2026-09-08
 */
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
@WebMvcTest(LegalBackfillController.class)
class LegalBackfillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyPort apiKeyPort;

    @MockBean
    private LegalBackfillService legalBackfillService;

    @MockBean
    private PipelineAsyncRunner asyncRunner;

    @BeforeEach
    void setUpAsyncRunner() {
        when(asyncRunner.runAsync(anyString(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable work = invocation.getArgument(1);
                    work.run();
                    Map<String, Object> accepted = new LinkedHashMap<>();
                    accepted.put("started", true);
                    accepted.put("pipelineId", invocation.getArgument(0));
                    accepted.put("message", "Pipeline started in background.");
                    return ResponseEntity.accepted().body(accepted);
                });
    }

    @Test
    void triggerBackfill_returns200() throws Exception {
        when(legalBackfillService.runBackfill(1095))
                .thenReturn(new BackfillResult(5, 10, 3, List.of()));

        mockMvc.perform(post("/monitoring/legal-backfill"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true));
    }

    @Test
    void triggerBackfill_defaultDays1095() throws Exception {
        when(legalBackfillService.runBackfill(1095))
                .thenReturn(new BackfillResult(0, 0, 0, List.of()));

        mockMvc.perform(post("/monitoring/legal-backfill"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true));

        verify(legalBackfillService).runBackfill(1095);
    }

    @Test
    void triggerBackfill_customDaysRespected() throws Exception {
        when(legalBackfillService.runBackfill(365))
                .thenReturn(new BackfillResult(2, 5, 1, List.of()));

        mockMvc.perform(post("/monitoring/legal-backfill?days=365"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true));

        verify(legalBackfillService).runBackfill(365);
    }

    @Test
    void triggerBackfill_reportsErrors() throws Exception {
        when(legalBackfillService.runBackfill(1095))
                .thenReturn(new BackfillResult(0, 5, 0, List.of("CourtListener failed: timeout")));

        mockMvc.perform(post("/monitoring/legal-backfill"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true));
    }
}
