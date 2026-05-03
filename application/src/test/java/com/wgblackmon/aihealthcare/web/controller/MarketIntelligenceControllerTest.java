package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.config.MarketIntelligenceScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link MarketIntelligenceController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
@WebMvcTest(MarketIntelligenceController.class)
class MarketIntelligenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketIntelligenceScheduler scheduler;

    // -------------------------------------------------------------------------
    // POST /api/v1/market-intelligence/refresh
    // -------------------------------------------------------------------------

    @Test
    void refresh_success_returns200WithFilePath() throws Exception {
        Path fakePath = Paths.get("NotebookLMDirectory/summaries/healthcare_ai_market_intelligence_2026_05_02.html")
                .toAbsolutePath();
        when(scheduler.generateAndExport()).thenReturn(fakePath);

        mockMvc.perform(post("/api/v1/market-intelligence/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filePath").isNotEmpty())
                .andExpect(jsonPath("$.reportDate").value("2026-05-02"))
                .andExpect(jsonPath("$.htmlLength").isNumber());
    }

    @Test
    void refresh_promptNotConfigured_returns500() throws Exception {
        when(scheduler.generateAndExport())
                .thenThrow(new IllegalStateException("No MARKET_INTELLIGENCE prompt configured"));

        mockMvc.perform(post("/api/v1/market-intelligence/refresh"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void refresh_ioError_returns500() throws Exception {
        when(scheduler.generateAndExport())
                .thenThrow(new IOException("disk full"));

        mockMvc.perform(post("/api/v1/market-intelligence/refresh"))
                .andExpect(status().isInternalServerError());
    }
}
