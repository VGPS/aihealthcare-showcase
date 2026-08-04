package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeCompanySentimentUseCase;
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
 * MockMvc tests for {@link SentimentRestController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@WebMvcTest(SentimentRestController.class)
class SentimentRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyzeCompanySentimentUseCase sentimentUseCase;

    @Test
    @WithMockUser
    void getAll_returnsSentimentList() throws Exception {
        when(sentimentUseCase.getAll()).thenReturn(List.of(
                buildSentiment("co1", "Company 1", SentimentLabel.POSITIVE, 0.7)));

        mockMvc.perform(get("/api/v1/sentiment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].companySlug").value("co1"));
    }

    @Test
    @WithMockUser
    void getAll_returnsEmptyArray() throws Exception {
        when(sentimentUseCase.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/sentiment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser
    void getBySlug_returnsSentiment() throws Exception {
        when(sentimentUseCase.getBySlug("tempus-ai")).thenReturn(
                Optional.of(buildSentiment("tempus-ai", "Tempus AI", SentimentLabel.POSITIVE, 0.6)));

        mockMvc.perform(get("/api/v1/sentiment/tempus-ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Tempus AI"))
                .andExpect(jsonPath("$.sentimentScore").value(0.6));
    }

    @Test
    @WithMockUser
    void getBySlug_returns404WhenNotFound() throws Exception {
        when(sentimentUseCase.getBySlug("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/sentiment/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void analyzeAll_triggersAnalysis() throws Exception {
        when(sentimentUseCase.analyzeAll()).thenReturn(List.of(
                buildSentiment("co1", "Co 1", SentimentLabel.NEUTRAL, 0.0)));

        mockMvc.perform(post("/api/v1/sentiment/analyze").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getAll_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/sentiment"))
                .andExpect(status().isUnauthorized());
    }

    private CompanySentiment buildSentiment(String slug, String name,
                                             SentimentLabel label, double score) {
        return new CompanySentiment(slug, name, label, score, 10, 5, 2, 2, 1,
                "Risk summary", List.of(), Instant.now());
    }
}
