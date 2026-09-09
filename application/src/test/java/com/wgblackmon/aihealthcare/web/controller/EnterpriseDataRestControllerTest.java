package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataArtifactPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link EnterpriseDataRestController}.
 *
 * <p>Validates authentication, tier enforcement (via service-layer exceptions),
 * 202+Location on submit, artifact download states, and ownership isolation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Import(SecurityConfig.class)
@WebMvcTest(EnterpriseDataRestController.class)
class EnterpriseDataRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestEnterpriseDataUseCase useCase;

    @MockitoBean
    private DataArtifactPort artifactPort;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    // ── Authentication ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/enterprise/data/feeds unauthenticated → redirect")
    void unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/v1/enterprise/data/feeds"))
                .andExpect(status().is3xxRedirection());
    }

    // ── Submit ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("POST /api/v1/enterprise/data/jobs → 202 + Location header")
    void submitJob_returns202WithLocation() throws Exception {
        DataJob queued = makeJob("job-1", DataJobStatus.QUEUED);
        when(useCase.submit(any(DataRequest.class))).thenReturn(queued);

        mockMvc.perform(post("/api/v1/enterprise/data/jobs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feedId":"articles","format":"CSV","rowLimit":100}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/enterprise/data/jobs/job-1"))
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    @WithMockUser(username = "free@test.com")
    @DisplayName("POST /api/v1/enterprise/data/jobs tier denied → 403")
    void submitJob_tierDenied_returns403() throws Exception {
        when(useCase.submit(any(DataRequest.class)))
                .thenThrow(new IllegalStateException("TIER_DENY"));

        mockMvc.perform(post("/api/v1/enterprise/data/jobs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feedId":"articles","format":"CSV","rowLimit":100}
                                """))
                .andExpect(status().isForbidden());
    }

    // ── Get Job ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("GET /api/v1/enterprise/data/jobs/{jobId} → 200")
    void getJob_exists_returns200() throws Exception {
        DataJob job = makeJob("job-1", DataJobStatus.RUNNING);
        when(useCase.getJob("job-1", "enterprise@test.com")).thenReturn(job);

        mockMvc.perform(get("/api/v1/enterprise/data/jobs/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("GET /api/v1/enterprise/data/jobs/{jobId} other owner → 404")
    void getJob_otherOwner_returns404() throws Exception {
        when(useCase.getJob("job-1", "enterprise@test.com"))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/api/v1/enterprise/data/jobs/job-1"))
                .andExpect(status().isNotFound());
    }

    // ── List Jobs ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("GET /api/v1/enterprise/data/jobs → 200 with list")
    void listJobs_returns200() throws Exception {
        when(useCase.listJobs("enterprise@test.com", 0, 20))
                .thenReturn(List.of(makeJob("job-1", DataJobStatus.SUCCEEDED)));

        mockMvc.perform(get("/api/v1/enterprise/data/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value("job-1"));
    }

    // ── Cancel ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("POST /api/v1/enterprise/data/jobs/{jobId}/cancel → 204")
    void cancelJob_running_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/enterprise/data/jobs/job-1/cancel").with(csrf()))
                .andExpect(status().isNoContent());

        verify(useCase).cancel("job-1", "enterprise@test.com");
    }

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("POST cancel terminal job → 409")
    void cancelJob_terminal_returns409() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("terminal"))
                .when(useCase).cancel("job-1", "enterprise@test.com");

        mockMvc.perform(post("/api/v1/enterprise/data/jobs/job-1/cancel").with(csrf()))
                .andExpect(status().isConflict());
    }

    // ── Feeds ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("GET /api/v1/enterprise/data/feeds → 200")
    void listFeeds_returns200() throws Exception {
        DataFeed feed = new DataFeed("articles", "Articles", "desc",
                DataSourceKind.INTERNAL_CORPUS, List.of(ExportFormat.CSV),
                List.of(), 100, 10000, "NONE", true);
        when(useCase.listFeeds("enterprise@test.com")).thenReturn(List.of(feed));

        mockMvc.perform(get("/api/v1/enterprise/data/feeds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].feedId").value("articles"))
                .andExpect(jsonPath("$[0].kind").value("INTERNAL_CORPUS"));
    }

    // ── Artifact download ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("GET artifact for RUNNING job → 409")
    void downloadArtifact_running_returns409() throws Exception {
        DataJob running = makeJob("job-1", DataJobStatus.RUNNING);
        when(useCase.getJob("job-1", "enterprise@test.com")).thenReturn(running);

        mockMvc.perform(get("/api/v1/enterprise/data/jobs/job-1/artifact"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("GET artifact for expired job → 410")
    void downloadArtifact_expired_returns410() throws Exception {
        DataJob expired = new DataJob(
                "job-1", "enterprise@test.com", null, DataJobMode.PULL, "articles", null,
                ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                10, 500L, "sha", "job-1.csv", "job-1.log", null, null,
                NOW, NOW, NOW, null,
                Instant.parse("2020-01-01T00:00:00Z"),
                null);
        when(useCase.getJob("job-1", "enterprise@test.com")).thenReturn(expired);

        mockMvc.perform(get("/api/v1/enterprise/data/jobs/job-1/artifact"))
                .andExpect(status().isGone());
    }

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("GET artifact for SUCCEEDED job → 200 with download")
    void downloadArtifact_succeeded_returns200() throws Exception {
        DataJob succeeded = new DataJob(
                "job-1", "enterprise@test.com", null, DataJobMode.PULL, "articles", null,
                ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                10, 500L, "sha", "job-1.csv", "job-1.log", null, null,
                NOW, NOW, NOW, null,
                Instant.parse("2030-12-31T00:00:00Z"),
                null);
        when(useCase.getJob("job-1", "enterprise@test.com")).thenReturn(succeeded);
        when(artifactPort.exists("job-1")).thenReturn(true);
        when(artifactPort.read("job-1")).thenReturn(
                new ByteArrayInputStream("id,name\n1,test".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/enterprise/data/jobs/job-1/artifact"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"job-1.csv\""))
                .andExpect(header().string("Content-Type", "text/csv"));
    }

    // ── Log ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("GET log for own job → 200")
    void readLog_ownJob_returns200() throws Exception {
        when(useCase.readLog("job-1", "enterprise@test.com", 0))
                .thenReturn("Fetching data...\n");

        mockMvc.perform(get("/api/v1/enterprise/data/jobs/job-1/log"))
                .andExpect(status().isOk())
                .andExpect(content().string("Fetching data...\n"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private DataJob makeJob(String jobId, DataJobStatus status) {
        return new DataJob(
                jobId, "enterprise@test.com", null, DataJobMode.PULL, "articles", null,
                ExportFormat.CSV, status,
                null, null, null, null, null, null, null,
                NOW, null, null, null, null, null);
    }
}
