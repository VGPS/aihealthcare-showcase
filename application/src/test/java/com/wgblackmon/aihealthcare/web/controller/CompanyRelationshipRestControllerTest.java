package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.CompanyRelationshipType;
import com.wgblackmon.aihealthcare.domain.port.inbound.MapCompanyRelationshipsUseCase;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link CompanyRelationshipRestController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Import(SecurityConfig.class)
@WebMvcTest(CompanyRelationshipRestController.class)
class CompanyRelationshipRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MapCompanyRelationshipsUseCase mapRelationshipsUseCase;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/relationships returns all relationships")
    void getAllRelationships_returnsJson() throws Exception {
        CompanyRelationship rel = new CompanyRelationship("r1", "Google", "DeepMind",
                CompanyRelationshipType.ACQUISITION, "a1", "Summary", 0.9, Instant.now());
        when(mapRelationshipsUseCase.getAllRelationships()).thenReturn(List.of(rel));

        mockMvc.perform(get("/api/v1/relationships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceCompany").value("Google"))
                .andExpect(jsonPath("$[0].targetCompany").value("DeepMind"))
                .andExpect(jsonPath("$[0].relationshipType").value("ACQUISITION"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/relationships?company=Google filters by company")
    void getAllRelationships_withCompanyFilter() throws Exception {
        when(mapRelationshipsUseCase.getRelationshipsForCompany("Google")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/relationships").param("company", "Google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(mapRelationshipsUseCase).getRelationshipsForCompany("Google");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/relationships/detect triggers detection")
    void triggerDetection_returnsCount() throws Exception {
        CompanyRelationship rel = new CompanyRelationship("r1", "A", "B",
                CompanyRelationshipType.PARTNERSHIP, "a1", "Sum", 0.7, Instant.now());
        when(mapRelationshipsUseCase.detectRelationships()).thenReturn(List.of(rel));

        mockMvc.perform(post("/api/v1/relationships/detect").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detected").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/relationships unauthenticated is permitted (API permitAll)")
    void getAllRelationships_unauthenticated_isPermitted() throws Exception {
        when(mapRelationshipsUseCase.getAllRelationships()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/relationships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
