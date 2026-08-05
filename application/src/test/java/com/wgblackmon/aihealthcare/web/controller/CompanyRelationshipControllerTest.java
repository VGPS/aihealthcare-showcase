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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link CompanyRelationshipController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Import(SecurityConfig.class)
@WebMvcTest(CompanyRelationshipController.class)
class CompanyRelationshipControllerTest {

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
    @DisplayName("GET /dashboard/relationships returns 200 with relationships")
    void relationshipsPage_withData_returnsView() throws Exception {
        CompanyRelationship rel = new CompanyRelationship("r1", "Google", "DeepMind",
                CompanyRelationshipType.ACQUISITION, "a1", "Google acquires DeepMind",
                0.9, Instant.now());
        when(mapRelationshipsUseCase.getAllRelationships()).thenReturn(List.of(rel));

        mockMvc.perform(get("/dashboard/relationships"))
                .andExpect(status().isOk())
                .andExpect(view().name("relationships"))
                .andExpect(model().attributeExists("relationships", "typeCounts",
                        "totalRelationships", "companyCount"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/relationships with no data returns empty state")
    void relationshipsPage_noData_returnsEmptyView() throws Exception {
        when(mapRelationshipsUseCase.getAllRelationships()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/relationships"))
                .andExpect(status().isOk())
                .andExpect(view().name("relationships"))
                .andExpect(model().attribute("totalRelationships", 0));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/relationships?company=Google filters by company")
    void relationshipsPage_withFilter_filtersResults() throws Exception {
        CompanyRelationship rel = new CompanyRelationship("r1", "Google", "DeepMind",
                CompanyRelationshipType.ACQUISITION, "a1", "Summary", 0.9, Instant.now());
        when(mapRelationshipsUseCase.getRelationshipsForCompany("Google")).thenReturn(List.of(rel));

        mockMvc.perform(get("/dashboard/relationships").param("company", "Google"))
                .andExpect(status().isOk())
                .andExpect(view().name("relationships"))
                .andExpect(model().attribute("filterCompany", "Google"));
    }

    @Test
    @DisplayName("GET /dashboard/relationships unauthenticated redirects to login")
    void relationshipsPage_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/dashboard/relationships"))
                .andExpect(status().is3xxRedirection());
    }
}
