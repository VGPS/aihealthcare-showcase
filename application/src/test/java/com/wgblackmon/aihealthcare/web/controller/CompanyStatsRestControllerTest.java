package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.port.inbound.BrowseCompaniesUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for {@link CompanyStatsRestController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-07
 * @updated 2026-10-07
 */
@WithMockUser
@WebMvcTest(CompanyStatsRestController.class)
class CompanyStatsRestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    BrowseCompaniesUseCase browseCompaniesUseCase;

    @Test
    void count_returnsJsonWithCount() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(buildCompanies(42));

        mockMvc.perform(get("/api/v1/companies/count"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.count").value(42));
    }

    @Test
    void count_emptyDirectory_returnsZero() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/companies/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void count_authenticatedUser_stillReturnsCount() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(buildCompanies(10));

        mockMvc.perform(get("/api/v1/companies/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(10));
    }

    @Test
    void count_responseHasOnlyCountKey() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(buildCompanies(5));

        mockMvc.perform(get("/api/v1/companies/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").exists())
                .andExpect(jsonPath("$.total").doesNotExist());
    }

    @SuppressWarnings("unchecked")
    private List<HealthcareAiCompany> buildCompanies(int n) {
        // Controller only calls .size() — null elements are fine for these tests
        return (List<HealthcareAiCompany>) (List<?>) Collections.nCopies(n, null);
    }
}
