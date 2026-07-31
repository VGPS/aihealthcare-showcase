package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
 * @version 3.0
 * @since   2026-07-30
 * @updated 2026-07-30
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

    // --- Page rendering ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void pipelinesRendersAdminPipelinesView() throws Exception {
        when(healthService.preFlightCheckAll(anyList())).thenReturn(Map.of());
        when(healthService.getAllLastRuns()).thenReturn(Map.of());

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

        mockMvc.perform(get("/admin/pipelines"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pipelineCount", 14));
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
}
