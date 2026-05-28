package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.model.ResearchSection;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link ResearchCompareController}.
 *
 * <p>Verifies that both pipelines are invoked when a query is present,
 * that no pipelines run when the query is absent, and that model
 * attributes are correctly populated for Thymeleaf rendering.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-05
 * @updated 2026-05-05
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(ResearchCompareController.class)
class ResearchCompareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConductResearchUseCase conductResearchUseCase;

    // -------------------------------------------------------------------------
    // GET /research/compare — no query
    // -------------------------------------------------------------------------

    @Test
    void compare_noQuery_returns200AndEmptyForm() throws Exception {
        mockMvc.perform(get("/research/compare"))
                .andExpect(status().isOk())
                .andExpect(view().name("research-compare"));

        verify(conductResearchUseCase, never()).conduct(any());
    }

    @Test
    void compare_noQuery_modelHasBlankQuery() throws Exception {
        mockMvc.perform(get("/research/compare"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("query", ""));
    }

    @Test
    void compare_blankQuery_doesNotRunPipelines() throws Exception {
        mockMvc.perform(get("/research/compare").param("query", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("research-compare"));

        verify(conductResearchUseCase, never()).conduct(any());
    }

    // -------------------------------------------------------------------------
    // GET /research/compare — with query
    // -------------------------------------------------------------------------

    @Test
    void compare_withQuery_returns200() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer("AI test"));

        mockMvc.perform(get("/research/compare").param("query", "AI in diagnostics"))
                .andExpect(status().isOk())
                .andExpect(view().name("research-compare"));
    }

    @Test
    void compare_withQuery_runsBothPipelines() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer("AI test"));

        mockMvc.perform(get("/research/compare").param("query", "AI in diagnostics"))
                .andExpect(status().isOk());

        verify(conductResearchUseCase, times(2)).conduct(any(ResearchRequest.class));
    }

    @Test
    void compare_withQuery_firstCallUsesLegacyMode() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer("AI"));
        ArgumentCaptor<ResearchRequest> captor = ArgumentCaptor.forClass(ResearchRequest.class);

        mockMvc.perform(get("/research/compare").param("query", "AI in surgery"))
                .andExpect(status().isOk());

        verify(conductResearchUseCase, times(2)).conduct(captor.capture());
        List<ResearchRequest> calls = captor.getAllValues();
        assertThat(calls.get(0).mode()).isEqualTo(ResearchMode.LEGACY_GOOGLE);
    }

    @Test
    void compare_withQuery_secondCallUsesStagedMode() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer("AI"));
        ArgumentCaptor<ResearchRequest> captor = ArgumentCaptor.forClass(ResearchRequest.class);

        mockMvc.perform(get("/research/compare").param("query", "AI in surgery"))
                .andExpect(status().isOk());

        verify(conductResearchUseCase, times(2)).conduct(captor.capture());
        List<ResearchRequest> calls = captor.getAllValues();
        assertThat(calls.get(1).mode()).isEqualTo(ResearchMode.STAGED_RESEARCH);
    }

    @Test
    void compare_withQuery_modelContainsLegacyAndStagedAnswers() throws Exception {
        ResearchAnswer legacy = sampleAnswer("AI");
        ResearchAnswer staged = sampleAnswer("AI");
        when(conductResearchUseCase.conduct(any()))
                .thenReturn(legacy)
                .thenReturn(staged);

        mockMvc.perform(get("/research/compare").param("query", "AI in radiology"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("legacyAnswer"))
                .andExpect(model().attributeExists("stagedAnswer"));
    }

    @Test
    void compare_withQuery_maxSourcesDefaultsTwenty() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer("AI"));
        ArgumentCaptor<ResearchRequest> captor = ArgumentCaptor.forClass(ResearchRequest.class);

        mockMvc.perform(get("/research/compare").param("query", "AI"))
                .andExpect(status().isOk());

        verify(conductResearchUseCase, times(2)).conduct(captor.capture());
        assertThat(captor.getAllValues().get(0).maxSources()).isEqualTo(20);
    }

    @Test
    void compare_maxSourcesCapped_atHundred() throws Exception {
        when(conductResearchUseCase.conduct(any())).thenReturn(sampleAnswer("AI"));
        ArgumentCaptor<ResearchRequest> captor = ArgumentCaptor.forClass(ResearchRequest.class);

        mockMvc.perform(get("/research/compare")
                .param("query", "AI")
                .param("maxSources", "999"))
                .andExpect(status().isOk());

        verify(conductResearchUseCase, times(2)).conduct(captor.capture());
        assertThat(captor.getAllValues().get(0).maxSources()).isEqualTo(100);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ResearchAnswer sampleAnswer(String query) {
        SourceCitation citation = new SourceCitation(1, "Sample Article",
                "https://example.com", Instant.now());
        ResearchSection section = new ResearchSection(
                "Research Findings",
                "Sample body with citation [1].",
                List.of(citation));
        return new ResearchAnswer(
                "answer-uuid-test",
                query,
                List.of(section),
                List.of(citation),
                Instant.now());
    }
}
