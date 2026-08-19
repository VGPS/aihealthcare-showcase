package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.GuidanceComparison;
import com.wgblackmon.aihealthcare.domain.marketanalysis.GuidanceQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice tests for {@link GuidanceController}.
 *
 * <p>All tests run as an authenticated ADMIN user to bypass the
 * {@code /api/**} authentication requirement in SecurityConfig.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@WebMvcTest(GuidanceController.class)
class GuidanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GuidanceQueryService guidanceQueryService;

    private static final BigDecimal LOW  = new BigDecimal("1.20");
    private static final BigDecimal HIGH = new BigDecimal("1.40");

    @Test
    @WithMockUser(roles = "ADMIN")
    void getGuidance_whenHistoryExists_returns200() throws Exception {
        GuidanceComparison g = new GuidanceComparison(
                "NVDA", LOW, HIGH, new BigDecimal("1.55"), new BigDecimal("1.65"), "EPS");
        when(guidanceQueryService.findLatestGuidance("NVDA", "EPS"))
                .thenReturn(Optional.of(g));

        mockMvc.perform(get("/api/market-digest/guidance/NVDA").param("metric", "EPS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickerSymbol").value("NVDA"))
                .andExpect(jsonPath("$.metric").value("EPS"))
                .andExpect(jsonPath("$.priorGuidanceLow").value(1.20))
                .andExpect(jsonPath("$.newGuidanceLow").value(1.55));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getGuidance_whenNoHistory_returns404() throws Exception {
        when(guidanceQueryService.findLatestGuidance("AAPL", "REVENUE"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/market-digest/guidance/AAPL").param("metric", "REVENUE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getGuidance_responseMapsAllFields() throws Exception {
        GuidanceComparison g = new GuidanceComparison(
                "MSFT", LOW, HIGH, new BigDecimal("2.00"), new BigDecimal("2.10"), "OPERATING_MARGIN");
        when(guidanceQueryService.findLatestGuidance("MSFT", "OPERATING_MARGIN"))
                .thenReturn(Optional.of(g));

        mockMvc.perform(get("/api/market-digest/guidance/MSFT").param("metric", "OPERATING_MARGIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorGuidanceLow").value(1.20))
                .andExpect(jsonPath("$.priorGuidanceHigh").value(1.40))
                .andExpect(jsonPath("$.newGuidanceLow").value(2.00))
                .andExpect(jsonPath("$.newGuidanceHigh").value(2.10))
                .andExpect(jsonPath("$.metric").value("OPERATING_MARGIN"));
    }

    @Test
    void getGuidance_unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(get("/api/market-digest/guidance/NVDA").param("metric", "EPS"))
                .andExpect(status().is4xxClientError());
    }
}
