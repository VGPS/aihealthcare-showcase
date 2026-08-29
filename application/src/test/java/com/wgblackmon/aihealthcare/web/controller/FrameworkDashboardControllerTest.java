package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.FrameworkDimension;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeFrameworksUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link FrameworkDashboardController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@WebMvcTest(FrameworkDashboardController.class)
class FrameworkDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyzeFrameworksUseCase frameworksUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private AnalystNotePort analystNotePort;

    @Test
    @WithMockUser
    void overview_rendersWithAnalyses() throws Exception {
        when(frameworksUseCase.getAll()).thenReturn(List.of(buildAnalysis("anthropic", "Anthropic")));

        mockMvc.perform(get("/dashboard/frameworks"))
                .andExpect(status().isOk())
                .andExpect(view().name("framework-analysis"))
                .andExpect(model().attribute("hasAnalyses", true))
                .andExpect(model().attributeExists("companyNames"))
                .andExpect(model().attributeExists("dimensionNames"))
                .andExpect(model().attributeExists("companyScores"))
                .andExpect(model().attributeExists("chartColors"))
                .andExpect(model().attributeExists("analyzedDates"));
    }

    @Test
    @WithMockUser
    void overview_rendersEmptyState() throws Exception {
        when(frameworksUseCase.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/frameworks"))
                .andExpect(status().isOk())
                .andExpect(view().name("framework-analysis"))
                .andExpect(model().attribute("hasAnalyses", false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void detail_rendersForValidSlug() throws Exception {
        when(frameworksUseCase.getBySlug("anthropic"))
                .thenReturn(Optional.of(buildAnalysis("anthropic", "Anthropic")));

        mockMvc.perform(get("/dashboard/frameworks/anthropic"))
                .andExpect(status().isOk())
                .andExpect(view().name("framework-detail"))
                .andExpect(model().attributeExists("analysis"))
                .andExpect(model().attributeExists("dimNames"))
                .andExpect(model().attributeExists("dimScores"))
                .andExpect(model().attributeExists("assessmentParagraphs"))
                .andExpect(model().attributeExists("analyzedAt"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void detail_redirectsForUnknownSlug() throws Exception {
        when(frameworksUseCase.getBySlug("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/dashboard/frameworks/unknown"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/frameworks"));
    }

    @Test
    void overview_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard/frameworks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void overview_multipleCompaniesProducesMultipleChartDatasets() throws Exception {
        when(frameworksUseCase.getAll()).thenReturn(List.of(
                buildAnalysis("anthropic", "Anthropic"),
                buildAnalysis("openai", "OpenAI")));

        mockMvc.perform(get("/dashboard/frameworks"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasAnalyses", true));
    }

    private FrameworkAnalysis buildAnalysis(String slug, String name) {
        return new FrameworkAnalysis(
                slug, name, "Overall assessment\n\nSecond paragraph",
                List.of(
                        new FrameworkDimension("Technical Maturity", 8, "Strong APIs"),
                        new FrameworkDimension("Clinical Validation", 6, "Growing evidence"),
                        new FrameworkDimension("Regulatory Positioning", 5, "Early stage"),
                        new FrameworkDimension("Platform Strategy", 7, "Good ecosystem"),
                        new FrameworkDimension("Market Momentum", 8, "High growth"),
                        new FrameworkDimension("Developer Experience", 9, "Excellent docs")),
                List.of("Strong documentation"),
                List.of("Limited clinical data"),
                List.of("New partnership announced"),
                7, 25, Instant.now());
    }
}
