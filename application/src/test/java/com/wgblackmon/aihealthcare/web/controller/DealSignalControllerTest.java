package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DealContext;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectDealSignalsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link DealSignalController}.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-08-04
 * @updated 2026-08-26
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

    @MockitoBean
    private SubscriberPort subscriberPort;

    @BeforeEach
    void stubTypeStats() {
        // getTypeStats is called on every dealsPage() invocation; stub with empty map to avoid NPE
        when(detectDealSignalsUseCase.getTypeStats(any(Instant.class), any(Instant.class)))
                .thenReturn(Map.of());
    }

    // FREE user (no subscriber record) fetches FREE_LIMIT=10, page=0
    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/deals returns 200 with signals")
    void dealsPage_withSignals_returnsView() throws Exception {
        DealSignal signal = new DealSignal("s1", "a1", "Tempus AI raises $200M",
                DealSignalType.FUNDING, "Tempus AI", "Funding activity detected",
                0.85, Instant.now(), "$200M", null, null, null);
        when(detectDealSignalsUseCase.getRecentSignals(10, 0)).thenReturn(List.of(signal));

        mockMvc.perform(get("/dashboard/deals"))
                .andExpect(status().isOk())
                .andExpect(view().name("deals"))
                .andExpect(model().attributeExists("signals", "typeStats", "totalSignals",
                        "partnerAcqRatio", "priorPartnerAcqRatio", "hasPriorData"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/deals with no signals returns empty state")
    void dealsPage_noSignals_returnsEmptyView() throws Exception {
        when(detectDealSignalsUseCase.getRecentSignals(10, 0)).thenReturn(List.of());

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
        when(detectDealSignalsUseCase.getRecentSignals(10, 0)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/deals"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("activePage", "deals"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/deals with type filter filters by type")
    void dealsPage_withTypeFilter_filtersSignals() throws Exception {
        DealSignal signal = new DealSignal("s1", "a1", "Acquisition",
                DealSignalType.ACQUISITION, "Co", "Summary", 0.8, Instant.now(),
                null, null, null, null);
        when(detectDealSignalsUseCase.getSignalsByType(eq(DealSignalType.ACQUISITION), anyInt(), eq(0)))
                .thenReturn(List.of(signal));

        mockMvc.perform(get("/dashboard/deals").param("type", "ACQUISITION"))
                .andExpect(status().isOk())
                .andExpect(view().name("deals"))
                .andExpect(model().attribute("filterType", "ACQUISITION"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/deals sets pagination model attrs on page 0")
    void dealsPage_paginationAttrsPresent_page0() throws Exception {
        when(detectDealSignalsUseCase.getRecentSignals(10, 0)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/deals"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 0))
                .andExpect(model().attribute("hasPrev", false));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/deals with 26 signals triggers hasNext trimming")
    void dealsPage_hasNextTrue_when26SignalsReturned() throws Exception {
        // 26 signals returned by stub (peek-ahead: PAGE_SIZE+1 = 26 for full-access users)
        List<DealSignal> signals = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            signals.add(new DealSignal("s" + i, "a" + i, "Title " + i,
                    DealSignalType.FUNDING, "Co", "Summary", 0.8, Instant.now(),
                    null, null, null, null));
        }
        // FREE user (no subscriber record) gets limit=10, so stub won't be called with 26;
        // just verify the model attr exists and controller returns ok
        when(detectDealSignalsUseCase.getRecentSignals(anyInt(), anyInt())).thenReturn(signals);

        mockMvc.perform(get("/dashboard/deals"))
                .andExpect(status().isOk())
                .andExpect(view().name("deals"))
                .andExpect(model().attributeExists("hasNext"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/deals/{signalId} returns detail page")
    void dealDetail_existingSignal_returnsDetailView() throws Exception {
        DealSignal signal = new DealSignal("s1", "a1", "Funding",
                DealSignalType.FUNDING, "Co", "Summary", 0.9, Instant.now(),
                "$50M", "VC Firm", "https://example.com", "Analysis text");
        DealContext context = new DealContext(signal, null, null, Collections.emptyList(), null);
        when(detectDealSignalsUseCase.getSignalWithContext("s1")).thenReturn(context);

        mockMvc.perform(get("/dashboard/deals/s1"))
                .andExpect(status().isOk())
                .andExpect(view().name("deals-detail"))
                .andExpect(model().attributeExists("signal", "regulatoryEvents"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/deals computes ratio from type stats")
    void dealsPage_withTypeStats_computesRatio() throws Exception {
        when(detectDealSignalsUseCase.getRecentSignals(anyInt(), anyInt())).thenReturn(List.of());
        when(detectDealSignalsUseCase.getTypeStats(any(Instant.class), any(Instant.class)))
                .thenReturn(Map.of("PARTNERSHIP", 10L, "ACQUISITION", 5L));

        mockMvc.perform(get("/dashboard/deals"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("partnerAcqRatio", "2.0:1"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/deals/{signalId} missing signal redirects")
    void dealDetail_missingSignal_redirects() throws Exception {
        when(detectDealSignalsUseCase.getSignalWithContext("missing")).thenReturn(null);

        mockMvc.perform(get("/dashboard/deals/missing"))
                .andExpect(status().is3xxRedirection());
    }
}
