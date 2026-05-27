package com.wgblackmon.aihealthcare.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.model.ResearchSection;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.web.dto.ResearchRequestDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link ResearchController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-26
 */
@WebMvcTest(ResearchController.class)
class ResearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConductResearchUseCase conductResearchUseCase;

    @MockitoBean
    private UsageTrackingPort usageTrackingPort;

    @MockitoBean
    private TierGatingService tierGatingService;

    // -------------------------------------------------------------------------
    // POST /api/v1/research — happy path
    // -------------------------------------------------------------------------

    @Test
    void research_validRequest_returns200() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer("What is AI?"));

        mockMvc.perform(post("/api/v1/research")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"query": "What is AI in healthcare?", "mode": "LEGACY_GOOGLE"}
                        """))
                .andExpect(status().isOk());
    }

    @Test
    void research_validRequest_responseBodyHasAnswerId() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer("AI test"));

        mockMvc.perform(post("/api/v1/research")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"query": "AI in clinical diagnostics"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerId").isNotEmpty());
    }

    @Test
    void research_validRequest_responseBodyHasSections() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer("AI test"));

        mockMvc.perform(post("/api/v1/research")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"query": "AI in clinical diagnostics"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections").isArray())
                .andExpect(jsonPath("$.sections[0].heading").isNotEmpty());
    }

    @Test
    void research_modeOmitted_defaultsToLegacyGoogle() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer("query"));
        ArgumentCaptor<ResearchRequest> captor = ArgumentCaptor.forClass(ResearchRequest.class);

        mockMvc.perform(post("/api/v1/research")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"query": "healthcare AI query"}
                        """))
                .andExpect(status().isOk());

        verify(conductResearchUseCase).conduct(captor.capture());
        assertThat(captor.getValue().mode()).isEqualTo(ResearchMode.LEGACY_GOOGLE);
    }

    @Test
    void research_maxSourcesOmitted_defaultsTwenty() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer("query"));
        ArgumentCaptor<ResearchRequest> captor = ArgumentCaptor.forClass(ResearchRequest.class);

        mockMvc.perform(post("/api/v1/research")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"query": "AI surgery"}
                        """))
                .andExpect(status().isOk());

        verify(conductResearchUseCase).conduct(captor.capture());
        assertThat(captor.getValue().maxSources()).isEqualTo(20);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/research — validation errors
    // -------------------------------------------------------------------------

    @Test
    void research_blankQuery_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/research")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"query": "   "}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void research_missingQuery_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/research")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mode": "LEGACY_GOOGLE"}
                        """))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ResearchAnswer sampleAnswer(String query) {
        SourceCitation citation = new SourceCitation(1, "Sample Article",
                "https://example.com", Instant.now());
        ResearchSection section = new ResearchSection(
                "Research Findings",
                "Sample body text with citation [1].",
                List.of(citation));
        return new ResearchAnswer(
                "answer-uuid-123",
                query,
                List.of(section),
                List.of(citation),
                Instant.now());
    }
}
