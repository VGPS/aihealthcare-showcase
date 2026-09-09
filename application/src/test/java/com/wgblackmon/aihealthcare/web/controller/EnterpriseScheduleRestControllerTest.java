package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageDataPushSchedulesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link EnterpriseScheduleRestController}.
 *
 * <p>Validates tier enforcement, ownership isolation, cron validation,
 * 201+nextRuns on create, and 202 on run-now.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Import(SecurityConfig.class)
@WebMvcTest(EnterpriseScheduleRestController.class)
class EnterpriseScheduleRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageDataPushSchedulesUseCase useCase;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    private DataPushSchedule makeSchedule(String id) {
        return new DataPushSchedule(
                id, "enterprise@test.com", "Daily Export", "articles",
                null, null, Map.of(), ExportFormat.CSV,
                "0 0 7 * * MON-FRI", "America/Chicago",
                List.of("r@test.com"), true,
                Instant.parse("2026-09-09T12:00:00Z"),
                null, null, null, 0, NOW, NOW);
    }

    // ── Authentication ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/enterprise/data/schedules unauthenticated → redirect")
    void unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/v1/enterprise/data/schedules"))
                .andExpect(status().is3xxRedirection());
    }

    // ── Tier enforcement ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "free@test.com")
    @DisplayName("POST create — non-ENTERPRISE tier → 403")
    void create_nonEnterprise_returns403() throws Exception {
        when(useCase.create(any(DataPushSchedule.class)))
                .thenThrow(new IllegalStateException("TIER_DENY"));

        mockMvc.perform(post("/api/v1/enterprise/data/schedules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Test","feedId":"articles","cronExpression":"0 0 7 * * MON-FRI",
                                 "zoneId":"UTC","recipients":["r@test.com"]}
                                """))
                .andExpect(status().isForbidden());
    }

    // ── Ownership isolation ────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("DELETE another owner's schedule → 404")
    void delete_otherOwner_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Schedule not found"))
                .when(useCase).delete("sched-other", "enterprise@test.com");

        mockMvc.perform(delete("/api/v1/enterprise/data/schedules/sched-other").with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ── Invalid cron ───────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("POST create — invalid cron → 400")
    void create_invalidCron_returns400() throws Exception {
        when(useCase.create(any(DataPushSchedule.class)))
                .thenThrow(new IllegalArgumentException("Invalid cron expression"));

        mockMvc.perform(post("/api/v1/enterprise/data/schedules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Test","feedId":"articles","cronExpression":"bad",
                                 "zoneId":"UTC","recipients":["r@test.com"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── Successful create ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("POST create → 201 with nextRuns[3]")
    void create_success_returns201WithNextRuns() throws Exception {
        DataPushSchedule created = makeSchedule("sched-1");
        when(useCase.create(any(DataPushSchedule.class))).thenReturn(created);
        when(useCase.previewNextRuns("0 0 7 * * MON-FRI", "America/Chicago", 3))
                .thenReturn(List.of(
                        Instant.parse("2026-09-09T12:00:00Z"),
                        Instant.parse("2026-09-10T12:00:00Z"),
                        Instant.parse("2026-09-11T12:00:00Z")));

        mockMvc.perform(post("/api/v1/enterprise/data/schedules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Daily Export","feedId":"articles",
                                 "cronExpression":"0 0 7 * * MON-FRI",
                                 "zoneId":"America/Chicago","format":"CSV",
                                 "recipients":["r@test.com"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.scheduleId").value("sched-1"))
                .andExpect(jsonPath("$.nextRuns").isArray())
                .andExpect(jsonPath("$.nextRuns.length()").value(3));
    }

    // ── Run now ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("POST run → 202 with job response")
    void runNow_returns202() throws Exception {
        DataJob job = new DataJob(
                "job-99", "enterprise@test.com", null, DataJobMode.PULL, "articles", null,
                ExportFormat.CSV, DataJobStatus.QUEUED,
                null, null, null, null, null, null, null,
                NOW, null, null, null, null, null);
        when(useCase.runNow("sched-1", "enterprise@test.com")).thenReturn(job);

        mockMvc.perform(post("/api/v1/enterprise/data/schedules/sched-1/run").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-99"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    // ── List ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("GET list → 200 with schedules")
    void list_returns200() throws Exception {
        when(useCase.list("enterprise@test.com"))
                .thenReturn(List.of(makeSchedule("sched-1")));

        mockMvc.perform(get("/api/v1/enterprise/data/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduleId").value("sched-1"))
                .andExpect(jsonPath("$[0].label").value("Daily Export"));
    }

    // ── Preview ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("GET preview → 200 with instants")
    void preview_returns200() throws Exception {
        when(useCase.previewNextRuns("0 0 7 * * MON-FRI", "UTC", 3))
                .thenReturn(List.of(
                        Instant.parse("2026-09-09T07:00:00Z"),
                        Instant.parse("2026-09-10T07:00:00Z"),
                        Instant.parse("2026-09-11T07:00:00Z")));

        mockMvc.perform(get("/api/v1/enterprise/data/schedules/preview")
                        .param("cron", "0 0 7 * * MON-FRI")
                        .param("zone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
