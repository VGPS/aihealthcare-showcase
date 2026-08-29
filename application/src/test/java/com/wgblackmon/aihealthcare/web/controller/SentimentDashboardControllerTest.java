package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeCompanySentimentUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link SentimentDashboardController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@WebMvcTest(SentimentDashboardController.class)
class SentimentDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyzeCompanySentimentUseCase sentimentUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private NewsArticleRepository articleRepository;

    @MockitoBean
    private AnalystNotePort analystNotePort;

    @Test
    @WithMockUser
    void riskDashboard_rendersWithSentiments() throws Exception {
        when(sentimentUseCase.getAll()).thenReturn(List.of(
                buildSentiment("tempus-ai", "Tempus AI", SentimentLabel.POSITIVE, 0.6)));

        mockMvc.perform(get("/dashboard/risk"))
                .andExpect(status().isOk())
                .andExpect(view().name("risk-dashboard"))
                .andExpect(model().attribute("hasSentiments", true))
                .andExpect(model().attribute("totalCompanies", 1));
    }

    @Test
    @WithMockUser
    void riskDashboard_rendersEmptyState() throws Exception {
        when(sentimentUseCase.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/risk"))
                .andExpect(status().isOk())
                .andExpect(view().name("risk-dashboard"))
                .andExpect(model().attribute("hasSentiments", false));
    }

    @Test
    @WithMockUser
    void riskDashboard_freeUserSeesLimitedCompanies() throws Exception {
        List<CompanySentiment> many = buildManySentiments(8);
        when(sentimentUseCase.getAll()).thenReturn(many);

        mockMvc.perform(get("/dashboard/risk"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("sentiments"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void riskDashboard_adminSeesAllCompanies() throws Exception {
        List<CompanySentiment> many = buildManySentiments(8);
        when(sentimentUseCase.getAll()).thenReturn(many);

        mockMvc.perform(get("/dashboard/risk"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", true));
    }

    @Test
    void riskDashboard_requiresAuth() throws Exception {
        mockMvc.perform(get("/dashboard/risk"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void companyDetail_rendersForValidSlug() throws Exception {
        when(sentimentUseCase.getBySlug("tempus-ai")).thenReturn(
                Optional.of(buildSentiment("tempus-ai", "Tempus AI", SentimentLabel.POSITIVE, 0.5)));

        mockMvc.perform(get("/dashboard/risk/tempus-ai"))
                .andExpect(status().isOk())
                .andExpect(view().name("risk-detail"))
                .andExpect(model().attributeExists("sentiment"))
                .andExpect(model().attributeExists("distributionData"))
                .andExpect(model().attributeExists("sortedArticles"))
                .andExpect(model().attributeExists("articleUrls"))
                .andExpect(model().attributeExists("articleSources"))
                .andExpect(model().attributeExists("articleDates"))
                .andExpect(model().attributeExists("sort"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void companyDetail_returns404ForMissingSlug() throws Exception {
        when(sentimentUseCase.getBySlug("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/dashboard/risk/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void riskDashboard_chartDataPopulated() throws Exception {
        when(sentimentUseCase.getAll()).thenReturn(List.of(
                buildSentiment("co1", "Company 1", SentimentLabel.POSITIVE, 0.7),
                buildSentiment("co2", "Company 2", SentimentLabel.NEGATIVE, -0.4)));

        mockMvc.perform(get("/dashboard/risk"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("chartLabels"))
                .andExpect(model().attributeExists("chartScores"))
                .andExpect(model().attributeExists("chartColors"));
    }

    private CompanySentiment buildSentiment(String slug, String name,
                                             SentimentLabel label, double score) {
        return new CompanySentiment(slug, name, label, score, 10, 5, 2, 2, 1,
                "Risk summary for " + name, List.of(), Instant.now());
    }

    private List<CompanySentiment> buildManySentiments(int count) {
        List<CompanySentiment> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(buildSentiment("company-" + i, "Company " + i,
                    SentimentLabel.NEUTRAL, 0.0));
        }
        return list;
    }
}
