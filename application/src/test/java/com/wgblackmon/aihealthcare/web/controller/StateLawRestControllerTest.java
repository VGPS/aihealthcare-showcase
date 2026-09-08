package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.LawChangeEvent;
import com.wgblackmon.aihealthcare.domain.model.LawSource;
import com.wgblackmon.aihealthcare.domain.model.LawStatus;
import com.wgblackmon.aihealthcare.domain.model.SourceType;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageStateLawsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineAsyncRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link StateLawRestController} REST endpoints.
 *
 * <p>All REST endpoints at {@code /api/v1/legislation/**} require
 * authentication. Tests verify JSON responses, 404 handling, and
 * filter/query delegation to the use case.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-08
 */
@WebMvcTest(StateLawRestController.class)
class StateLawRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageStateLawsUseCase legislationUseCase;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockBean
    private PipelineAsyncRunner asyncRunner;

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

    @Test
    @WithMockUser
    void getAllLaws_returnsJson() throws Exception {
        when(legislationUseCase.getAll()).thenReturn(List.of(createTestLaw()));

        mockMvc.perform(get("/api/v1/legislation/state-laws"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("ca-ab-3030"))
                .andExpect(jsonPath("$[0].stateCode").value("CA"))
                .andExpect(jsonPath("$[0].title").value("Health care services: artificial intelligence"));
    }

    @Test
    @WithMockUser
    void getLawById_found_returns200() throws Exception {
        when(legislationUseCase.getById("ca-ab-3030")).thenReturn(Optional.of(createTestLaw()));

        mockMvc.perform(get("/api/v1/legislation/state-laws/ca-ab-3030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ca-ab-3030"))
                .andExpect(jsonPath("$.stateName").value("California"))
                .andExpect(jsonPath("$.billNumber").value("AB 3030"))
                .andExpect(jsonPath("$.status").value("ENACTED"))
                .andExpect(jsonPath("$.categories[0]").value("PROVIDER_DISCLOSURE_CONSENT"));
    }

    @Test
    @WithMockUser
    void getLawById_notFound_returns404() throws Exception {
        when(legislationUseCase.getById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/legislation/state-laws/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getUpcoming_returnsJson() throws Exception {
        when(legislationUseCase.getUpcoming(90)).thenReturn(List.of(createTestLaw()));

        mockMvc.perform(get("/api/v1/legislation/state-laws/upcoming").param("days", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("ca-ab-3030"));
    }

    @Test
    @WithMockUser
    void getByState_returnsGroupedMap() throws Exception {
        StateLaw caLaw = createTestLaw();
        StateLaw txLaw = new StateLaw("tx-hb-1709", StateCode.TX, "Texas", "HB 1709",
                "AI in Health Insurance", 2025, null, null, null, null,
                LawStatus.ENACTED, null, List.of(LawCategory.PAYER_UTILIZATION_REVIEW),
                "Insurers", "Requirements", "TDI",
                List.of(new LawSource(SourceType.OFFICIAL, "https://capitol.texas.gov",
                        null, null, null, false)),
                null, "1.0",
                Instant.parse("2026-09-06T00:00:00Z"),
                Instant.parse("2026-09-06T00:00:00Z"));
        when(legislationUseCase.getAll()).thenReturn(List.of(caLaw, txLaw));

        mockMvc.perform(get("/api/v1/legislation/state-laws/by-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CA").isArray())
                .andExpect(jsonPath("$.CA.length()").value(1))
                .andExpect(jsonPath("$.TX").isArray())
                .andExpect(jsonPath("$.TX.length()").value(1));
    }

    @Test
    void getAllLaws_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/legislation/state-laws"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getAllLaws_withStateFilter_delegatesToUseCase() throws Exception {
        when(legislationUseCase.getByState(StateCode.CA)).thenReturn(List.of(createTestLaw()));

        mockMvc.perform(get("/api/v1/legislation/state-laws").param("state", "CA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser
    void getAllLaws_empty_returnsEmptyArray() throws Exception {
        when(legislationUseCase.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/legislation/state-laws"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getChanges_admin_returnsJson() throws Exception {
        LawChangeEvent event = new LawChangeEvent(1L, "ca-ab-3030",
                Instant.parse("2026-09-07T10:30:00Z"), "CONTENT_CHANGED",
                "Source content hash changed", false);
        when(legislationUseCase.getUnreviewedChanges()).thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/legislation/changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lawId").value("ca-ab-3030"))
                .andExpect(jsonPath("$[0].changeType").value("CONTENT_CHANGED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reviewChange_admin_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/legislation/changes/42/review")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(legislationUseCase).reviewChange(42L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void triggerRefresh_admin_returnsCompletedStatus() throws Exception {
        when(legislationUseCase.triggerRefresh()).thenReturn(3);

        mockMvc.perform(post("/api/v1/legislation/refresh")
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true));
    }

    // --- Helper ---

    private StateLaw createTestLaw() {
        return new StateLaw("ca-ab-3030", StateCode.CA, "California", "AB 3030",
                "Health care services: artificial intelligence", 2024,
                "2024-09-28", null, "2025-01-01", null,
                LawStatus.ENACTED, "Enacted",
                List.of(LawCategory.PROVIDER_DISCLOSURE_CONSENT),
                "Health care providers", "Must disclose AI use", "California DPA",
                List.of(new LawSource(SourceType.OFFICIAL,
                        "https://leginfo.legislature.ca.gov",
                        null, null, null, false)),
                null, "2026-09-06",
                Instant.parse("2026-09-06T00:00:00Z"),
                Instant.parse("2026-09-06T00:00:00Z"));
    }
}
