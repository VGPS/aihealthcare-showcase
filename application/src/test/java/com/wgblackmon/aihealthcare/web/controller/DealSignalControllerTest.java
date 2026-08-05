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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link DealSignalController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Import(SecurityConfig.class)
@WebMvcTest(DealSignalController.class)
class DealSignalControllerTest {

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
    @DisplayName("GET /dashboard/deals returns 200 with signals")
    void dealsPage_withSignals_returnsView() throws Exception {
        DealSignal signal = new DealSignal("s1", "a1", "Tempus AI raises $200M",
                DealSignalType.FUNDING, "Tempus AI", "Funding activity detected",
                0.85, Instant.now());
        when(detectDealSignalsUseCase.getRecentSignals(50)).thenReturn(List.of(signal));

        mockMvc.perform(get("/dashboard/deals"))
                .andExpect(status().isOk())
                .andExpect(view().name("deals"))
                .andExpect(model().attributeExists("signals", "typeCounts", "totalSignals"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/deals with no signals returns empty state")
    void dealsPage_noSignals_returnsEmptyView() throws Exception {
        when(detectDealSignalsUseCase.getRecentSignals(50)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/deals"))
                .andExpect(status().isOk())
                .andExpect(view().name("deals"))
                .andExpect(model().attribute("totalSignals", 0));
    }

    @Test
    @DisplayName("GET /dashboard/deals unauthenticated redirects to login")
    void dealsPage_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/dashboard/deals"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/deals sets activePage to deals")
    void dealsPage_setsActivePage() throws Exception {
        when(detectDealSignalsUseCase.getRecentSignals(50)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/deals"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("activePage", "deals"));
    }
}
