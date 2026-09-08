package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DealContext;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectDealSignalsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineAsyncRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
 * @updated 2026-09-08
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

    @MockBean
    private PipelineAsyncRunner asyncRunner;

    @BeforeEach
    void setUpAsyncRunner() {
        when(asyncRunner.runAsync(anyString(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable work = invocation.getArgument(1);
                    work.run();
                    Map<String, Object> accepted = new LinkedHashMap<>();
                    accepted.put("started", true);
                    accepted.put("pipelineId", invocation.getArgument(0));
                    accepted.put("message", "Pipeline started in background.");
                    return ResponseEntity.accepted().body(accepted);
                });
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/deals returns recent signals")
    void getRecentSignals_returnsJson() throws Exception {
        DealSignal signal = new DealSignal("s1", "a1", "Funding News",
                DealSignalType.FUNDING, "Acme", "Summary", 0.85, Instant.now(),
                "$100M", "VC Fund", null, null);
        when(detectDealSignalsUseCase.getRecentSignals(50, 0)).thenReturn(List.of(signal));

        mockMvc.perform(get("/api/v1/deals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signalId").value("s1"))
                .andExpect(jsonPath("$[0].signalType").value("FUNDING"))
                .andExpect(jsonPath("$[0].companyName").value("Acme"))
                .andExpect(jsonPath("$[0].dealAmount").value("$100M"))
                .andExpect(jsonPath("$[0].counterpartyName").value("VC Fund"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/deals with custom limit")
    void getRecentSignals_withCustomLimit() throws Exception {
        when(detectDealSignalsUseCase.getRecentSignals(10, 0)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/deals").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(detectDealSignalsUseCase).getRecentSignals(10, 0);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/deals/detect triggers detection")
    void triggerDetection_returnsCount() throws Exception {
        DealSignal signal = new DealSignal("s1", "a1", "IPO News",
                DealSignalType.IPO, "Co", "Summary", 0.7, Instant.now(),
                null, null, null, null);
        when(detectDealSignalsUseCase.detectSignals()).thenReturn(List.of(signal));

        mockMvc.perform(post("/api/v1/deals/detect").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/deals unauthenticated redirects to login")
    void getRecentSignals_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/v1/deals"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/deals with type filter returns filtered results")
    void getRecentSignals_withTypeFilter_filtersResults() throws Exception {
        DealSignal signal = new DealSignal("s1", "a1", "IPO News",
                DealSignalType.IPO, "Co", "Summary", 0.7, Instant.now(),
                null, null, null, null);
        when(detectDealSignalsUseCase.getSignalsByType(DealSignalType.IPO, 50, 0))
                .thenReturn(List.of(signal));

        mockMvc.perform(get("/api/v1/deals").param("type", "IPO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signalType").value("IPO"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/deals/{signalId} returns deal context")
    void getSignalDetail_existingSignal_returnsContext() throws Exception {
        DealSignal signal = new DealSignal("s1", "a1", "Funding",
                DealSignalType.FUNDING, "Co", "Summary", 0.9, Instant.now(),
                "$50M", null, null, "Analysis");
        DealContext context = new DealContext(signal, null, null, Collections.emptyList(), null);
        when(detectDealSignalsUseCase.getSignalWithContext("s1")).thenReturn(context);

        mockMvc.perform(get("/api/v1/deals/s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signal.signalId").value("s1"))
                .andExpect(jsonPath("$.hasSentiment").value(false))
                .andExpect(jsonPath("$.regulatoryEventCount").value(0));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/deals/{signalId} missing returns 404")
    void getSignalDetail_missingSignal_returns404() throws Exception {
        when(detectDealSignalsUseCase.getSignalWithContext("missing")).thenReturn(null);

        mockMvc.perform(get("/api/v1/deals/missing"))
                .andExpect(status().isNotFound());
    }
}
