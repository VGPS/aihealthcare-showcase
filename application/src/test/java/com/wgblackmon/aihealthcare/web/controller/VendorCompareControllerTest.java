package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.model.VendorAssessment;
import com.wgblackmon.aihealthcare.domain.model.VendorCompareResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.CompareVendorsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link VendorCompareController}.
 *
 * <p>All pipeline calls are mocked — no real AI or retrieval calls are made.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-14
 * @updated 2026-06-08
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(VendorCompareController.class)
class VendorCompareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompareVendorsUseCase compareVendorsUseCase;

    @Test
    void getVendors_noQuery_returnsFormPageWithoutCallingPipeline() throws Exception {
        mockMvc.perform(get("/research/vendors"))
                .andExpect(status().isOk())
                .andExpect(view().name("vendor-compare"))
                .andExpect(model().attribute("hasResults", false));

        verify(compareVendorsUseCase, never()).compare(anyString(), anyInt(), anyInt(), anyString());
    }

    @Test
    void getVendors_withQuery_returnsVendorListAndCitations() throws Exception {
        List<VendorAssessment> vendors = List.of(
                new VendorAssessment("Anthropic",
                        List.of("Strong reasoning", "Privacy controls"),
                        List.of("High cost"),
                        0.9, 9, 10),
                new VendorAssessment("OpenAI",
                        List.of("GPT-4 vision"),
                        List.of("Data retention concerns"),
                        0.7, 7, 10));

        List<SourceCitation> citations = List.of(
                new SourceCitation(1, "AI in Healthcare", "https://ex.com/1", Instant.now()),
                new SourceCitation(2, "ML for Diagnosis", "https://ex.com/2", Instant.now()));

        VendorCompareResult result = new VendorCompareResult(vendors, citations);
        when(compareVendorsUseCase.compare(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(result);

        mockMvc.perform(get("/research/vendors").param("query", "AI diagnostics"))
                .andExpect(status().isOk())
                .andExpect(view().name("vendor-compare"))
                .andExpect(model().attribute("vendors", vendors))
                .andExpect(model().attribute("citations", citations))
                .andExpect(model().attribute("hasResults", true));
    }

    @Test
    void getVendors_withQuery_emptyResultShowsNoResults() throws Exception {
        VendorCompareResult emptyResult = new VendorCompareResult(
                Collections.emptyList(), Collections.emptyList());
        when(compareVendorsUseCase.compare(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(emptyResult);

        mockMvc.perform(get("/research/vendors").param("query", "obscure topic"))
                .andExpect(status().isOk())
                .andExpect(view().name("vendor-compare"))
                .andExpect(model().attribute("hasResults", false));
    }

    @Test
    void getVendors_pipelineThrows_displaysErrorMessageAndEmptyList() throws Exception {
        when(compareVendorsUseCase.compare(anyString(), anyInt(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("AI service unavailable"));

        mockMvc.perform(get("/research/vendors").param("query", "AI in surgery"))
                .andExpect(status().isOk())
                .andExpect(view().name("vendor-compare"))
                .andExpect(model().attribute("hasResults", false))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void getVendors_maxSourcesParamIsCappedAt100() throws Exception {
        VendorCompareResult emptyResult = new VendorCompareResult(
                Collections.emptyList(), Collections.emptyList());
        when(compareVendorsUseCase.compare(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(emptyResult);

        mockMvc.perform(get("/research/vendors")
                        .param("query", "AI diagnostics")
                        .param("maxSources", "999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("maxSources", 100));
    }

    @Test
    void getVendors_defaultMinVendorsIs5() throws Exception {
        VendorCompareResult emptyResult = new VendorCompareResult(
                Collections.emptyList(), Collections.emptyList());
        when(compareVendorsUseCase.compare(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(emptyResult);

        mockMvc.perform(get("/research/vendors")
                        .param("query", "AI diagnostics"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("minVendors", 5));
    }

    @Test
    void getVendors_minVendorsParamIsCappedAt20() throws Exception {
        VendorCompareResult emptyResult = new VendorCompareResult(
                Collections.emptyList(), Collections.emptyList());
        when(compareVendorsUseCase.compare(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(emptyResult);

        mockMvc.perform(get("/research/vendors")
                        .param("query", "AI diagnostics")
                        .param("minVendors", "50"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("minVendors", 20));
    }
}
