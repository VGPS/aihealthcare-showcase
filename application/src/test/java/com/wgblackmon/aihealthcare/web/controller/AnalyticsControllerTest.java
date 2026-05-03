package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CountByLabel;
import com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;
import com.wgblackmon.aihealthcare.domain.model.VariantScore;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link AnalyticsController}.
 *
 * <p>Covers the three GET analytics endpoints: ingestion, runs, and evaluations.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetAnalyticsUseCase analyticsUseCase;

    // -------------------------------------------------------------------------
    // GET /api/v1/analytics/ingestion
    // -------------------------------------------------------------------------

    @Test
    void getIngestionAnalytics_returns200WithCounts() throws Exception {
        IngestionAnalytics analytics = new IngestionAnalytics(
                150L,
                List.of(new CountByLabel("ACADEMIC", 90L), new CountByLabel("INDUSTRY", 60L)),
                List.of(new CountByLabel("PubMed AI Healthcare", 90L)),
                12L, 45L);
        when(analyticsUseCase.getIngestionAnalytics()).thenReturn(analytics);

        mockMvc.perform(get("/api/v1/analytics/ingestion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArticles").value(150))
                .andExpect(jsonPath("$.last7DaysCount").value(12))
                .andExpect(jsonPath("$.last30DaysCount").value(45))
                .andExpect(jsonPath("$.bySourceTier[0].label").value("ACADEMIC"))
                .andExpect(jsonPath("$.bySourceTier[0].count").value(90))
                .andExpect(jsonPath("$.byTopic[0].label").value("PubMed AI Healthcare"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/analytics/runs
    // -------------------------------------------------------------------------

    @Test
    void getRunAnalytics_returns200WithStatusCounts() throws Exception {
        RunAnalytics analytics = new RunAnalytics(8L, 3L, 4L, 1L, Instant.parse("2026-04-30T08:00:00Z"));
        when(analyticsUseCase.getRunAnalytics()).thenReturn(analytics);

        mockMvc.perform(get("/api/v1/analytics/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(8))
                .andExpect(jsonPath("$.draftCount").value(3))
                .andExpect(jsonPath("$.sentCount").value(4))
                .andExpect(jsonPath("$.archivedCount").value(1));
    }

    @Test
    void getRunAnalytics_noRuns_mostRecentRunAtIsNull() throws Exception {
        RunAnalytics analytics = new RunAnalytics(0L, 0L, 0L, 0L, null);
        when(analyticsUseCase.getRunAnalytics()).thenReturn(analytics);

        mockMvc.perform(get("/api/v1/analytics/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(0))
                .andExpect(jsonPath("$.mostRecentRunAt").doesNotExist());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/analytics/evaluations
    // -------------------------------------------------------------------------

    @Test
    void getEvaluationAnalytics_returns200WithVariantScores() throws Exception {
        VariantScore vs = new VariantScore("v1", "Baseline", 5L, 0.82, 0.78, 0.85, 0.80, 0.83, 0.84);
        EvaluationAnalytics analytics = new EvaluationAnalytics(5L, 2L, List.of(vs), "v1");
        when(analyticsUseCase.getEvaluationAnalytics()).thenReturn(analytics);

        mockMvc.perform(get("/api/v1/analytics/evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvaluations").value(5))
                .andExpect(jsonPath("$.totalComparisons").value(2))
                .andExpect(jsonPath("$.bestVariantId").value("v1"))
                .andExpect(jsonPath("$.variantScores[0].variantId").value("v1"))
                .andExpect(jsonPath("$.variantScores[0].evaluationCount").value(5))
                .andExpect(jsonPath("$.variantScores[0].avgOverall").value(0.82));
    }

    @Test
    void getEvaluationAnalytics_noEvaluations_bestVariantIdIsNull() throws Exception {
        EvaluationAnalytics analytics = new EvaluationAnalytics(0L, 0L, List.of(), null);
        when(analyticsUseCase.getEvaluationAnalytics()).thenReturn(analytics);

        mockMvc.perform(get("/api/v1/analytics/evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvaluations").value(0))
                .andExpect(jsonPath("$.variantScores").isEmpty())
                .andExpect(jsonPath("$.bestVariantId").doesNotExist());
    }
}
