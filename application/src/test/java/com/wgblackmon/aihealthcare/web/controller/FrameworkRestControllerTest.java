package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.FrameworkDimension;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeFrameworksUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link FrameworkRestController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@WebMvcTest(FrameworkRestController.class)
class FrameworkRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyzeFrameworksUseCase frameworksUseCase;

    @Test
    @WithMockUser
    void getAll_returnsAnalyses() throws Exception {
        when(frameworksUseCase.getAll()).thenReturn(List.of(buildAnalysis("anthropic", "Anthropic")));

        mockMvc.perform(get("/api/v1/frameworks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companySlug").value("anthropic"))
                .andExpect(jsonPath("$[0].overallScore").value(7));
    }

    @Test
    @WithMockUser
    void getAll_returnsEmptyListWhenNone() throws Exception {
        when(frameworksUseCase.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/frameworks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser
    void getBySlug_returnsAnalysis() throws Exception {
        when(frameworksUseCase.getBySlug("anthropic"))
                .thenReturn(Optional.of(buildAnalysis("anthropic", "Anthropic")));

        mockMvc.perform(get("/api/v1/frameworks/anthropic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Anthropic"))
                .andExpect(jsonPath("$.dimensions[0].name").value("Technical Maturity"));
    }

    @Test
    @WithMockUser
    void getBySlug_returns404WhenNotFound() throws Exception {
        when(frameworksUseCase.getBySlug("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/frameworks/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void analyzeAll_triggersAndReturnsResults() throws Exception {
        when(frameworksUseCase.analyzeAll()).thenReturn(List.of(buildAnalysis("openai", "OpenAI")));

        mockMvc.perform(post("/api/v1/frameworks/analyze").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companySlug").value("openai"));
    }

    private FrameworkAnalysis buildAnalysis(String slug, String name) {
        return new FrameworkAnalysis(
                slug, name, "Overall assessment text",
                List.of(new FrameworkDimension("Technical Maturity", 7, "Strong APIs")),
                List.of("Good docs"), List.of("Limited scope"),
                List.of("New feature"), 7, 10, Instant.now());
    }
}
