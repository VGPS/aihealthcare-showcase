package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.LegalTrendSignal;
import com.wgblackmon.aihealthcare.domain.model.LegalTrendSnapshot;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectLegalTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineAsyncRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link LegalTrendController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-09-08
 */
@WebMvcTest(LegalTrendController.class)
class LegalTrendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DetectLegalTrendsUseCase detectLegalTrendsUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private ArticleIngestionPort articleIngestionPort;

    @MockitoBean
    private MonitorRegulatoryEventsUseCase regulatoryUseCase;

    @MockBean
    private PipelineAsyncRunner asyncRunner;

    @MockitoBean
    private TierResolver tierResolver;

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

    private static LegalTrendSnapshot sampleSnapshot() {
        LegalTrendSignal signal = new LegalTrendSignal(
                "FDA enforcement", "REGULATION", 10, 3,
                3.33, TrendDirection.RISING, List.of("a1"));
        return new LegalTrendSnapshot(Instant.now(), 30, List.of(signal), 42);
    }

    @Test
    @WithMockUser
    void getPage_withSnapshot_populatesModel() throws Exception {
        when(detectLegalTrendsUseCase.getLatestSnapshot())
                .thenReturn(Optional.of(sampleSnapshot()));

        mockMvc.perform(get("/dashboard/legal/trends"))
                .andExpect(status().isOk())
                .andExpect(view().name("legal-trends"))
                .andExpect(model().attribute("hasSnapshot", true))
                .andExpect(model().attribute("totalKeywords", 42));
    }

    @Test
    @WithMockUser
    void getPage_noSnapshot_emptyModel() throws Exception {
        when(detectLegalTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.empty());

        mockMvc.perform(get("/dashboard/legal/trends"))
                .andExpect(status().isOk())
                .andExpect(view().name("legal-trends"))
                .andExpect(model().attribute("hasSnapshot", false));
    }

    @Test
    @WithMockUser
    void getPage_freeUser_limitedTrends() throws Exception {
        List<LegalTrendSignal> manySignals = List.of(
                new LegalTrendSignal("k1", "LITIGATION", 10, 2, 5.0, TrendDirection.RISING, List.of()),
                new LegalTrendSignal("k2", "REGULATION", 9, 2, 4.5, TrendDirection.RISING, List.of()),
                new LegalTrendSignal("k3", "POLICY", 8, 2, 4.0, TrendDirection.RISING, List.of()),
                new LegalTrendSignal("k4", "LITIGATION", 7, 2, 3.5, TrendDirection.RISING, List.of()),
                new LegalTrendSignal("k5", "REGULATION", 6, 2, 3.0, TrendDirection.RISING, List.of())
        );
        LegalTrendSnapshot snapshot = new LegalTrendSnapshot(
                Instant.now(), 30, manySignals, 100);
        when(detectLegalTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/dashboard/legal/trends"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPage_admin_fullAccess() throws Exception {
        when(tierResolver.isAdmin(any())).thenReturn(true);
        when(tierResolver.hasFullAccess(any())).thenReturn(true);
        when(detectLegalTrendsUseCase.getLatestSnapshot())
                .thenReturn(Optional.of(sampleSnapshot()));

        mockMvc.perform(get("/dashboard/legal/trends"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void triggerDetection_admin_redirects() throws Exception {
        when(tierResolver.isAdmin(any())).thenReturn(true);
        when(tierResolver.hasFullAccess(any())).thenReturn(true);
        LegalTrendSnapshot snapshot = new LegalTrendSnapshot(
                Instant.now(), 30, List.of(), 0);
        when(detectLegalTrendsUseCase.detectLegalTrends()).thenReturn(snapshot);

        mockMvc.perform(post("/dashboard/legal/trends/detect").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/legal/trends"));

        verify(detectLegalTrendsUseCase).detectLegalTrends();
    }

    @Test
    @WithMockUser
    void triggerDetection_nonAdmin_redirectsWithoutDetecting() throws Exception {
        mockMvc.perform(post("/dashboard/legal/trends/detect").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/legal/trends"));
    }

    @Test
    void getPage_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard/legal/trends"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getPage_includesChartData() throws Exception {
        when(detectLegalTrendsUseCase.getLatestSnapshot())
                .thenReturn(Optional.of(sampleSnapshot()));

        mockMvc.perform(get("/dashboard/legal/trends"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("chartLabels", "chartData"));
    }

    @Test
    @WithMockUser
    void getPage_includesCategoryCounts() throws Exception {
        when(detectLegalTrendsUseCase.getLatestSnapshot())
                .thenReturn(Optional.of(sampleSnapshot()));

        mockMvc.perform(get("/dashboard/legal/trends"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("litigationCount", "regulationCount", "policyCount"));
    }
}
