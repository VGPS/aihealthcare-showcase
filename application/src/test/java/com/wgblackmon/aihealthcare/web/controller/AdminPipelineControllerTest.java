package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService;
import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;
import com.wgblackmon.aihealthcare.domain.model.PipelineStepStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.NewsletterGenerationScheduler;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link AdminPipelineController}.
 *
 * @author  Bill Blackmon
 * @version 3.2
 * @since   2026-07-30
 * @updated 2026-08-19
 */
@Import(SecurityConfig.class)
@WebMvcTest(AdminPipelineController.class)
class AdminPipelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private PipelineHealthService healthService;

    @MockitoBean
    private NewsletterGenerationScheduler newsletterScheduler;

    @MockitoBean
    private MarketDigestService marketDigestService;

    // --- Page rendering ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void pipelinesRendersAdminPipelinesView() throws Exception {
        when(healthService.preFlightCheckAll(anyList())).thenReturn(Map.of());
        when(healthService.getAllLastRuns()).thenReturn(Map.of());
        when(healthService.getRecentHistory(50)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/pipelines"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-pipelines"))
                .andExpect(model().attributeExists("pipelines", "pipelineCount", "serverTime",
                        "preFlightWarnings", "lastRunSummaries", "lastRunTimes", "lastRunStatuses"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pipelinesContainsExpectedPipelineCount() throws Exception {
        when(healthService.preFlightCheckAll(anyList())).thenReturn(Map.of());
        when(healthService.getAllLastRuns()).thenReturn(Map.of());
        when(healthService.getRecentHistory(50)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/pipelines"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pipelineCount", 19));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pipelinesShowsLastRunInfo() throws Exception {
        Instant start = Instant.now().minusSeconds(5);
        Instant end = Instant.now();
        PipelineHealthService.PipelineRunRecord run =
                PipelineHealthService.PipelineRunRecord.success("rss-feeds", 42, start, end);

        when(healthService.preFlightCheckAll(anyList())).thenReturn(Map.of());
        when(healthService.getAllLastRuns()).thenReturn(Map.of("rss-feeds", run));
        when(healthService.getRecentHistory(50)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/pipelines"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("lastRunSummaries", "lastRunStatuses"));
    }

    // --- Security ---

    @Test
    @WithMockUser(roles = "USER")
    void pipelinesReturns403ForNonAdmin() throws Exception {
        mockMvc.perform(get("/admin/pipelines"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pipelinesRedirectsToLoginWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/admin/pipelines"))
                .andExpect(status().is3xxRedirection());
    }

    // --- Pre-flight endpoint ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void preflightReturnsWarningsMap() throws Exception {
        when(healthService.preFlightCheckAll(anyList()))
                .thenReturn(Map.of("wiki-compile", List.of("ANTHROPIC_API_KEY not configured")));

        mockMvc.perform(get("/admin/pipelines/preflight"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wiki-compile[0]").value("ANTHROPIC_API_KEY not configured"));
    }

    // --- Dry-run endpoint ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void dryRunReturnsResult() throws Exception {
        PipelineHealthService.DryRunResult result =
                new PipelineHealthService.DryRunResult("rss-feeds", "READY", List.of(),
                        "Will harvest articles from all configured RSS feeds");
        when(healthService.dryRun("rss-feeds")).thenReturn(result);

        mockMvc.perform(get("/admin/pipelines/dry-run/rss-feeds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.pipelineId").value("rss-feeds"));
    }

    // --- Record endpoint ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void recordRunAcceptsSuccessResult() throws Exception {
        mockMvc.perform(post("/admin/pipelines/record/rss-feeds").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCESS\",\"message\":\"\",\"durationMs\":1234}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value("true"));

        verify(healthService).recordRun(eq("rss-feeds"),
                org.mockito.ArgumentMatchers.argThat(r -> "SUCCESS".equals(r.status())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void recordRunAcceptsFailureResult() throws Exception {
        mockMvc.perform(post("/admin/pipelines/record/wiki-compile").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\",\"message\":\"API key missing\",\"durationMs\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value("true"));

        verify(healthService).recordRun(eq("wiki-compile"),
                org.mockito.ArgumentMatchers.argThat(r -> "FAILED".equals(r.status())));
    }

    // --- Last runs endpoint ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void lastRunsReturnsEmptyMapWhenNoneRecorded() throws Exception {
        when(healthService.getAllLastRuns()).thenReturn(Map.of());

        mockMvc.perform(get("/admin/pipelines/last-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- Run history ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void pipelinesIncludesRecentEvents() throws Exception {
        PipelineRunEvent event = new PipelineRunEvent(
                1L, "rss-feeds", "RSS Feed Harvest", PipelineStepStatus.SUCCESS,
                Instant.now().minusSeconds(60), Instant.now(), 60000L, null, 42, "ORCHESTRATOR"
        );

        when(healthService.preFlightCheckAll(anyList())).thenReturn(Map.of());
        when(healthService.getAllLastRuns()).thenReturn(Map.of());
        when(healthService.getRecentHistory(50)).thenReturn(List.of(event));

        mockMvc.perform(get("/admin/pipelines"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("recentEvents", "eventTimestamps"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pipelinesHandlesEmptyHistory() throws Exception {
        when(healthService.preFlightCheckAll(anyList())).thenReturn(Map.of());
        when(healthService.getAllLastRuns()).thenReturn(Map.of());
        when(healthService.getRecentHistory(50)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/pipelines"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-pipelines"))
                .andExpect(model().attributeExists("recentEvents", "eventTimestamps"));
    }

    // --- Newsletter generate-and-send endpoint ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateAndSendNewsletterReturnsSuccess() throws Exception {
        mockMvc.perform(post("/admin/pipelines/newsletter/generate-and-send").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Newsletter generated and sent"));

        verify(newsletterScheduler).runDailyDraftGeneration();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateAndSendNewsletterReturnsFailureOnException() throws Exception {
        doThrow(new RuntimeException("SMTP connection refused"))
                .when(newsletterScheduler).runDailyDraftGeneration();

        mockMvc.perform(post("/admin/pipelines/newsletter/generate-and-send").with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("SMTP connection refused"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void generateAndSendNewsletterReturns403ForNonAdmin() throws Exception {
        mockMvc.perform(post("/admin/pipelines/newsletter/generate-and-send").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- Market digest generate endpoint ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateMarketDigestReturnsSuccess() throws Exception {
        MarketDigest digest = new MarketDigest(LocalDate.now(), java.util.List.of(), Instant.now());
        when(marketDigestService.generateDailyDigest(LocalDate.now())).thenReturn(digest);

        mockMvc.perform(post("/admin/pipelines/market-digest/generate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.entryCount").value(0))
                .andExpect(jsonPath("$.date").value(LocalDate.now().toString()));

        verify(marketDigestService).generateDailyDigest(LocalDate.now());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateMarketDigestReturnsFailureOnException() throws Exception {
        when(marketDigestService.generateDailyDigest(LocalDate.now()))
                .thenThrow(new RuntimeException("Perplexity API unavailable"));

        mockMvc.perform(post("/admin/pipelines/market-digest/generate").with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Perplexity API unavailable"));
    }
}
