package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectDealSignalsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link DealSignalRestController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Import(SecurityConfig.class)
@WebMvcTest(DealSignalRestController.class)
class DealSignalRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DetectDealSignalsUseCase detectDealSignalsUseCase;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/deals returns recent signals")
    void getRecentSignals_returnsJson() throws Exception {
        DealSignal signal = new DealSignal("s1", "a1", "Funding News",
                DealSignalType.FUNDING, "Acme", "Summary", 0.85, Instant.now());
        when(detectDealSignalsUseCase.getRecentSignals(50)).thenReturn(List.of(signal));

        mockMvc.perform(get("/api/v1/deals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signalId").value("s1"))
                .andExpect(jsonPath("$[0].signalType").value("FUNDING"))
                .andExpect(jsonPath("$[0].companyName").value("Acme"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/deals with custom limit")
    void getRecentSignals_withCustomLimit() throws Exception {
        when(detectDealSignalsUseCase.getRecentSignals(10)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/deals").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(detectDealSignalsUseCase).getRecentSignals(10);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/deals/detect triggers detection")
    void triggerDetection_returnsCount() throws Exception {
        DealSignal signal = new DealSignal("s1", "a1", "IPO News",
                DealSignalType.IPO, "Co", "Summary", 0.7, Instant.now());
        when(detectDealSignalsUseCase.detectSignals()).thenReturn(List.of(signal));

        mockMvc.perform(post("/api/v1/deals/detect").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detected").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/deals unauthenticated still accessible (API is permitAll)")
    void getRecentSignals_unauthenticated_isPermitted() throws Exception {
        when(detectDealSignalsUseCase.getRecentSignals(50)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/deals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
