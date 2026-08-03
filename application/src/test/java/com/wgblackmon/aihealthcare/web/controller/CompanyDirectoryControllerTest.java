package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link CompanyDirectoryController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
@WebMvcTest(CompanyDirectoryController.class)
class CompanyDirectoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthcareAiCompanyPort companyPort;

    @Test
    @WithMockUser
    void companiesPage_rendersWithCompanies() throws Exception {
        HealthcareAiCompany company = company("id-1", "Tempus AI", "diagnostics", true);
        when(companyPort.findAll()).thenReturn(List.of(company));

        mockMvc.perform(get("/dashboard/companies"))
                .andExpect(status().isOk())
                .andExpect(view().name("companies"))
                .andExpect(model().attribute("companyCount", 1))
                .andExpect(model().attribute("validatedCount", 1));
    }

    @Test
    @WithMockUser
    void companiesPage_emptyState() throws Exception {
        when(companyPort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/companies"))
                .andExpect(status().isOk())
                .andExpect(view().name("companies"))
                .andExpect(model().attribute("companyCount", 0));
    }

    @Test
    @WithMockUser
    void companiesPage_filterBySubSector() throws Exception {
        HealthcareAiCompany c1 = company("id-1", "Tempus AI", "diagnostics", true);
        HealthcareAiCompany c2 = company("id-2", "Abridge", "clinical documentation", true);
        when(companyPort.findAll()).thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/dashboard/companies").param("filter", "diagnostics"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("companyCount", 1))
                .andExpect(model().attribute("filter", "diagnostics"));
    }

    @Test
    @WithMockUser
    void companiesPage_sortByFunding() throws Exception {
        when(companyPort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/companies").param("sort", "funding"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "funding"));
    }

    @Test
    void companiesPage_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard/companies"))
                .andExpect(status().isUnauthorized());
    }

    private HealthcareAiCompany company(String id, String name, String subSector, boolean validated) {
        return new HealthcareAiCompany(
                id, name, name.toLowerCase(), name.toLowerCase().replace(" ", "") + ".com",
                "An AI healthcare company", "San Francisco, CA", 2019,
                "Healthcare AI", subSector, "Series B", "$50M", null,
                List.of("https://src.com"), validated, List.of("https://val.com"),
                Instant.now(), Instant.now());
    }
}
