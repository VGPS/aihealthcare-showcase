package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.LawSource;
import com.wgblackmon.aihealthcare.domain.model.LawStatus;
import com.wgblackmon.aihealthcare.domain.model.SourceType;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageStateLawsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * @updated 2026-09-06
 */
@WebMvcTest(StateLawRestController.class)
class StateLawRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageStateLawsUseCase legislationUseCase;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

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
