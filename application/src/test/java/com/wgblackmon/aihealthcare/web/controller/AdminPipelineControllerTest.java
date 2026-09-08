package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ProduceMarketDigestUseCase;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionService;
import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;
import com.wgblackmon.aihealthcare.domain.model.PipelineStepStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.NewsletterGenerationScheduler;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineAsyncRunner;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
 * @version 3.5
 * @since   2026-07-30
 * @updated 2026-09-08
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
    private PipelineAsyncRunner asyncRunner;

    @MockitoBean
    private NewsletterGenerationScheduler newsletterScheduler;

    @MockitoBean
    private ProduceMarketDigestUseCase marketDigestService;

    @MockitoBean
    private DeliverNewsletterUseCase deliverUseCase;

    @MockitoBean
    private PriceReactionService priceReactionService;

    @MockitoBean
    private DetectTrendsUseCase detectTrendsUseCase;

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
                .andExpect(model().attribute("pipelineCount", 24));
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
                Instant.now().minusSeconds(60), Instant.now(), 60000L, null, 42, "ORCHESTRATOR",
                null, null, null
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
    void generateAndSendNewsletterReturns202() throws Exception {
        when(asyncRunner.runAsync(eq("newsletter-send"), any(Runnable.class)))
                .thenReturn(accepted202("newsletter-send"));

        mockMvc.perform(post("/admin/pipelines/newsletter/generate-and-send").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true))
                .andExpect(jsonPath("$.pipelineId").value("newsletter-send"));

        verify(asyncRunner).runAsync(eq("newsletter-send"), any(Runnable.class));
    }

    @Test
    @WithMockUser(roles = "USER")
    void generateAndSendNewsletterReturns403ForNonAdmin() throws Exception {
        mockMvc.perform(post("/admin/pipelines/newsletter/generate-and-send").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- Digest send endpoint ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void sendDigestReturns202() throws Exception {
        when(asyncRunner.runAsync(eq("digest-send"), any(Runnable.class)))
                .thenReturn(accepted202("digest-send"));

        mockMvc.perform(post("/admin/pipelines/digest/send").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true))
                .andExpect(jsonPath("$.pipelineId").value("digest-send"));

        verify(asyncRunner).runAsync(eq("digest-send"), any(Runnable.class));
    }

    @Test
    @WithMockUser(roles = "USER")
    void sendDigestReturns403ForNonAdmin() throws Exception {
        mockMvc.perform(post("/admin/pipelines/digest/send").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- Market digest generate endpoint ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateMarketDigestReturns202() throws Exception {
        when(asyncRunner.runAsync(eq("market-digest"), any(Runnable.class)))
                .thenReturn(accepted202("market-digest"));

        mockMvc.perform(post("/admin/pipelines/market-digest/generate").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true))
                .andExpect(jsonPath("$.pipelineId").value("market-digest"));

        verify(asyncRunner).runAsync(eq("market-digest"), any(Runnable.class));
    }

    // --- Price reaction capture endpoint ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void captureMarketPriceReactionsReturns202() throws Exception {
        when(asyncRunner.runAsync(eq("price-reaction-capture"), any(Runnable.class)))
                .thenReturn(accepted202("price-reaction-capture"));

        mockMvc.perform(post("/admin/pipelines/market-digest/price-reactions/capture").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true))
                .andExpect(jsonPath("$.pipelineId").value("price-reaction-capture"));

        verify(asyncRunner).runAsync(eq("price-reaction-capture"), any(Runnable.class));
    }

    // --- Trend detection endpoint ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void runTrendDetectionReturns202() throws Exception {
        when(asyncRunner.runAsync(eq("trend-detection"), any(Runnable.class)))
                .thenReturn(accepted202("trend-detection"));

        mockMvc.perform(post("/admin/pipelines/trend-detection/run").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true))
                .andExpect(jsonPath("$.pipelineId").value("trend-detection"));

        verify(asyncRunner).runAsync(eq("trend-detection"), any(Runnable.class));
    }

    // --- Helper ---

    private static ResponseEntity<Map<String, Object>> accepted202(String pipelineId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("started", true);
        body.put("pipelineId", pipelineId);
        body.put("message", "Pipeline started in background. Check run history for results.");
        return ResponseEntity.accepted().body(body);
    }
}
