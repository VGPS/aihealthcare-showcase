package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchSection;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for usage metering in {@link ResearchController}.
 *
 * <p>Verifies that the {@code X-Subscriber-Email} header triggers usage checking
 * and that HTTP 429 is returned when the monthly limit is reached.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(ResearchController.class)
class ResearchControllerUsageTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private ConductResearchUseCase conductResearchUseCase;

    @MockitoBean
    private UsageTrackingPort usageTrackingPort;

    @MockitoBean
    private TierGatingService tierGatingService;

    private static final String QUERY_JSON = """
            {"query": "AI in radiology"}
            """;

    @Test
    void research_noEmailHeader_allowedWithoutUsageCheck() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer());

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUERY_JSON))
                .andExpect(status().isOk());

        verify(usageTrackingPort, never()).getOrCreateUsage(anyString(), anyString());
    }

    @Test
    void research_withEmailHeader_underLimit_allowed() throws Exception {
        UsageRecord usage = new UsageRecord("user@example.com", "2026-05", 5, 15);
        when(usageTrackingPort.getOrCreateUsage(anyString(), anyString())).thenReturn(usage);
        when(tierGatingService.canQuery(usage)).thenReturn(true);
        when(usageTrackingPort.incrementAndGet(anyString(), anyString()))
                .thenReturn(new UsageRecord("user@example.com", "2026-05", 6, 15));
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer());

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Subscriber-Email", "user@example.com")
                        .content(QUERY_JSON))
                .andExpect(status().isOk());

        verify(usageTrackingPort).incrementAndGet(anyString(), anyString());
    }

    @Test
    void research_withEmailHeader_atLimit_returns429() throws Exception {
        UsageRecord usage = new UsageRecord("user@example.com", "2026-05", 15, 15);
        when(usageTrackingPort.getOrCreateUsage(anyString(), anyString())).thenReturn(usage);
        when(tierGatingService.canQuery(usage)).thenReturn(false);

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Subscriber-Email", "user@example.com")
                        .content(QUERY_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Monthly query limit reached"))
                .andExpect(jsonPath("$.used").value(15))
                .andExpect(jsonPath("$.limit").value(15));

        verify(conductResearchUseCase, never()).conduct(any());
    }

    @Test
    void research_withEmailHeader_overLimit_returns429() throws Exception {
        UsageRecord usage = new UsageRecord("user@example.com", "2026-05", 20, 15);
        when(usageTrackingPort.getOrCreateUsage(anyString(), anyString())).thenReturn(usage);
        when(tierGatingService.canQuery(usage)).thenReturn(false);

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Subscriber-Email", "user@example.com")
                        .content(QUERY_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.used").value(20))
                .andExpect(jsonPath("$.limit").value(15));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ResearchAnswer sampleAnswer() {
        SourceCitation citation = new SourceCitation(1, "Sample", "https://example.com", Instant.now());
        ResearchSection section = new ResearchSection("Findings", "Body [1].", List.of(citation));
        return new ResearchAnswer("answer-123", "query", List.of(section), List.of(citation), Instant.now());
    }
}
