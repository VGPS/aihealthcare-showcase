package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageDataPushSchedulesUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link EnterpriseDataConsoleController}.
 *
 * <p>Validates correct view names, model attributes, and ENTERPRISE-only access.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-09-08
 * @updated 2026-09-08 — ED-2: scheduleUseCase mock added
 */
@Import(SecurityConfig.class)
@WebMvcTest(EnterpriseDataConsoleController.class)
class EnterpriseDataConsoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestEnterpriseDataUseCase useCase;

    @MockitoBean
    private ManageDataPushSchedulesUseCase scheduleUseCase;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    private static final String OWNER = "enterprise@test.com";
    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    // ── Main console page ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("GET /enterprise/data → correct view + model attributes")
    void console_returnsCorrectView() throws Exception {
        DataFeed feed = new DataFeed("articles", "Articles", "desc",
                DataSourceKind.INTERNAL_CORPUS, List.of(ExportFormat.CSV),
                List.of(), 100, 10000, "NONE", true);
        when(useCase.listFeeds(OWNER)).thenReturn(List.of(feed));
        when(useCase.listJobs(OWNER, 0, 50)).thenReturn(List.of());

        when(scheduleUseCase.list(OWNER)).thenReturn(List.of());

        mockMvc.perform(get("/enterprise/data"))
                .andExpect(status().isOk())
                .andExpect(view().name("enterprise-data-console"))
                .andExpect(model().attributeExists("feeds"))
                .andExpect(model().attributeExists("jobs"))
                .andExpect(model().attributeExists("schedules"))
                .andExpect(model().attribute("hasInFlightJobs", false));
    }

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("GET /enterprise/data with in-flight jobs sets hasInFlightJobs=true")
    void console_withRunningJob_hasInFlightJobsTrue() throws Exception {
        when(useCase.listFeeds(OWNER)).thenReturn(List.of());
        DataJob running = makeJob("job-1", DataJobStatus.RUNNING);
        when(useCase.listJobs(OWNER, 0, 50)).thenReturn(List.of(running));

        mockMvc.perform(get("/enterprise/data"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasInFlightJobs", true));
    }

    // ── Job rows fragment ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("GET /enterprise/data/jobs/rows → fragment view")
    void jobRows_returnsFragment() throws Exception {
        when(useCase.listJobs(OWNER, 0, 50)).thenReturn(List.of());

        mockMvc.perform(get("/enterprise/data/jobs/rows"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/enterprise-job-rows"))
                .andExpect(model().attributeExists("jobs"));
    }

    // ── Log tail fragment ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("GET /enterprise/data/jobs/{id}/log-tail → fragment view")
    void logTail_returnsFragment() throws Exception {
        when(useCase.readLog("job-1", OWNER, 0)).thenReturn("Running...\n");

        mockMvc.perform(get("/enterprise/data/jobs/job-1/log-tail"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/enterprise-log-tail"))
                .andExpect(model().attributeExists("logContent"))
                .andExpect(model().attribute("jobId", "job-1"));
    }

    // ── Preview fragment ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("GET /enterprise/data/jobs/{id}/preview → fragment view")
    void preview_succeededJob_returnsFragment() throws Exception {
        DataJob succeeded = makeJob("job-1", DataJobStatus.SUCCEEDED);
        when(useCase.getJob("job-1", OWNER)).thenReturn(succeeded);

        mockMvc.perform(get("/enterprise/data/jobs/job-1/preview"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/enterprise-job-preview"))
                .andExpect(model().attributeExists("job"))
                .andExpect(model().attribute("isSucceeded", true));
    }

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("GET /enterprise/data/jobs/{id}/preview not found → fragment with null job")
    void preview_notFound_returnsFragmentWithNullJob() throws Exception {
        when(useCase.getJob("missing", OWNER))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/enterprise/data/jobs/missing/preview"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/enterprise-job-preview"))
                .andExpect(model().attribute("isSucceeded", false));
    }

    // ── Unauthenticated ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /enterprise/data unauthenticated → redirect")
    void unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/enterprise/data"))
                .andExpect(status().is3xxRedirection());
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private DataJob makeJob(String jobId, DataJobStatus status) {
        return new DataJob(
                jobId, OWNER, null, DataJobMode.PULL, "articles", null,
                ExportFormat.CSV, status,
                status == DataJobStatus.SUCCEEDED ? 10 : null,
                status == DataJobStatus.SUCCEEDED ? 500L : null,
                null, null, null, null, null,
                NOW, null, null, null, null, null);
    }
}
