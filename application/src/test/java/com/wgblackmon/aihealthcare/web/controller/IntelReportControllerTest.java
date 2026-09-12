package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.IntelReport;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateIntelReportUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link IntelReportController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@WebMvcTest(IntelReportController.class)
class IntelReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GenerateIntelReportUseCase intelReportUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockBean
    private TierResolver tierResolver;

    @Test
    @WithMockUser(username = "free@test.com")
    void listReports_freeUser_showsUpgradePrompt() throws Exception {
        when(tierResolver.resolveTier(any(Principal.class))).thenReturn(SubscriptionTier.FREE);
        when(tierResolver.isAdmin(any())).thenReturn(false);

        mockMvc.perform(get("/research/intel"))
                .andExpect(status().isOk())
                .andExpect(view().name("intel-reports"))
                .andExpect(model().attribute("fullAccess", false));

        verify(intelReportUseCase, never()).findAll();
    }

    @Test
    @WithMockUser(username = "sub@test.com")
    void listReports_subscriber_showsReports() throws Exception {
        when(tierResolver.resolveTier(any(Principal.class))).thenReturn(SubscriptionTier.SUBSCRIBER);
        when(tierResolver.isAdmin(any())).thenReturn(false);
        when(tierResolver.hasFullAccess(any())).thenReturn(true);
        when(intelReportUseCase.findAll()).thenReturn(List.of(
                report("rpt-1", "Anthropic"),
                report("rpt-2", "Tempus")));

        mockMvc.perform(get("/research/intel"))
                .andExpect(status().isOk())
                .andExpect(view().name("intel-reports"))
                .andExpect(model().attribute("fullAccess", true))
                .andExpect(model().attributeExists("reports", "reportDates"));
    }

    @Test
    @WithMockUser(username = "sub@test.com")
    void viewReport_existingReport_rendersDetailPage() throws Exception {
        when(tierResolver.resolveTier(any(Principal.class))).thenReturn(SubscriptionTier.SUBSCRIBER);
        when(tierResolver.isAdmin(any())).thenReturn(false);
        when(tierResolver.hasFullAccess(any())).thenReturn(true);
        when(intelReportUseCase.findById("rpt-1")).thenReturn(Optional.of(report("rpt-1", "Anthropic")));

        mockMvc.perform(get("/research/intel/rpt-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("intel-report-detail"))
                .andExpect(model().attributeExists("report", "generatedDate"));
    }

    @Test
    @WithMockUser(username = "sub@test.com")
    void viewReport_notFound_returnsListWithError() throws Exception {
        when(tierResolver.resolveTier(any(Principal.class))).thenReturn(SubscriptionTier.SUBSCRIBER);
        when(tierResolver.isAdmin(any())).thenReturn(false);
        when(tierResolver.hasFullAccess(any())).thenReturn(true);
        when(intelReportUseCase.findById("missing")).thenReturn(Optional.empty());
        when(intelReportUseCase.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/research/intel/missing"))
                .andExpect(status().isOk())
                .andExpect(view().name("intel-reports"))
                .andExpect(model().attributeExists("errorMessage", "reportDates"));
    }

    @Test
    @WithMockUser(username = "sub@test.com")
    void generateReport_subscriber_redirectsToDetail() throws Exception {
        when(tierResolver.resolveTier(any(Principal.class))).thenReturn(SubscriptionTier.SUBSCRIBER);
        when(tierResolver.isAdmin(any())).thenReturn(false);
        when(tierResolver.hasFullAccess(any())).thenReturn(true);
        when(intelReportUseCase.generate("Anthropic healthcare", "sub@test.com"))
                .thenReturn(report("rpt-new", "Anthropic healthcare"));

        mockMvc.perform(post("/research/intel/generate")
                        .param("query", "Anthropic healthcare")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/research/intel/*"));
    }

    @Test
    @WithMockUser(username = "free@test.com")
    void generateReport_freeUser_deniesAccess() throws Exception {
        when(tierResolver.resolveTier(any(Principal.class))).thenReturn(SubscriptionTier.FREE);
        when(tierResolver.isAdmin(any())).thenReturn(false);

        mockMvc.perform(post("/research/intel/generate")
                        .param("query", "Anthropic healthcare")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("intel-reports"))
                .andExpect(model().attribute("fullAccess", false));

        verify(intelReportUseCase, never()).generate(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "sub@test.com")
    void generateReport_aiFailure_showsErrorOnListPage() throws Exception {
        when(tierResolver.resolveTier(any(Principal.class))).thenReturn(SubscriptionTier.SUBSCRIBER);
        when(tierResolver.isAdmin(any())).thenReturn(false);
        when(tierResolver.hasFullAccess(any())).thenReturn(true);
        when(intelReportUseCase.generate("bad query", "sub@test.com"))
                .thenThrow(new RuntimeException("AI service unavailable"));
        when(intelReportUseCase.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/research/intel/generate")
                        .param("query", "bad query")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("intel-reports"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    private IntelReport report(String id, String query) {
        return new IntelReport(id, query, "<h2>Summary</h2><p>Content</p>",
                5, "sub@test.com", Instant.now(), List.of());
    }
}
