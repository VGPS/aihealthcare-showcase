package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.model.VendorAssessment;
import com.wgblackmon.aihealthcare.domain.model.VendorCompareResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.CompareVendorsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceConfig;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
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
 * @version 2.0
 * @since   2026-05-14
 * @updated 2026-07-07
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(VendorCompareController.class)
class VendorCompareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private CompareVendorsUseCase compareVendorsUseCase;

    @MockitoBean
    private FeedSourceProperties feedSourceProperties;

    @Test
    void getVendors_noQuery_returnsFormPageWithoutCallingPipeline() throws Exception {
        when(feedSourceProperties.toFeedSourceConfigs()).thenReturn(competitorConfigs());

        mockMvc.perform(get("/research/vendors"))
                .andExpect(status().isOk())
                .andExpect(view().name("vendor-compare"))
                .andExpect(model().attribute("hasResults", false))
                .andExpect(model().attributeExists("availableVendors"));

        verify(compareVendorsUseCase, never()).compare(anyString(), anyInt(), anyInt(), anyString());
        verify(compareVendorsUseCase, never()).compareSelected(anyList(), anyString(), anyInt(), anyString());
    }

    @Test
    void getVendors_withFreeFormQuery_callsLegacyCompare() throws Exception {
        when(feedSourceProperties.toFeedSourceConfigs()).thenReturn(competitorConfigs());

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

        verify(compareVendorsUseCase).compare(eq("AI diagnostics"), anyInt(), anyInt(), anyString());
    }

    @Test
    void getVendors_withVendorCheckboxes_callsCompareSelected() throws Exception {
        when(feedSourceProperties.toFeedSourceConfigs()).thenReturn(competitorConfigs());

        List<VendorAssessment> vendors = List.of(
                new VendorAssessment("Anthropic",
                        List.of("Claude reasoning"), List.of("Cost"),
                        0.8, 8, 10),
                new VendorAssessment("OpenAI",
                        List.of("GPT ecosystem"), List.of("Privacy"),
                        0.6, 6, 10));

        VendorCompareResult result = new VendorCompareResult(vendors, Collections.emptyList());
        when(compareVendorsUseCase.compareSelected(anyList(), nullable(String.class), anyInt(), anyString()))
                .thenReturn(result);

        mockMvc.perform(get("/research/vendors")
                        .param("vendors", "Anthropic Healthcare", "OpenAI Healthcare"))
                .andExpect(status().isOk())
                .andExpect(view().name("vendor-compare"))
                .andExpect(model().attribute("vendors", vendors))
                .andExpect(model().attribute("hasResults", true));

        verify(compareVendorsUseCase).compareSelected(
                eq(List.of("Anthropic Healthcare", "OpenAI Healthcare")),
                nullable(String.class), anyInt(), anyString());
    }

    @Test
    void getVendors_withVendorCheckboxesAndFocusArea_passesFocusArea() throws Exception {
        when(feedSourceProperties.toFeedSourceConfigs()).thenReturn(competitorConfigs());

        VendorCompareResult result = new VendorCompareResult(
                Collections.emptyList(), Collections.emptyList());
        when(compareVendorsUseCase.compareSelected(anyList(), anyString(), anyInt(), anyString()))
                .thenReturn(result);

        mockMvc.perform(get("/research/vendors")
                        .param("vendors", "Anthropic Healthcare")
                        .param("focusArea", "radiology"))
                .andExpect(status().isOk())
                .andExpect(view().name("vendor-compare"));

        verify(compareVendorsUseCase).compareSelected(
                eq(List.of("Anthropic Healthcare")),
                eq("radiology"), anyInt(), anyString());
    }

    @Test
    void getVendors_emptyResultShowsNoResults() throws Exception {
        when(feedSourceProperties.toFeedSourceConfigs()).thenReturn(competitorConfigs());

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
        when(feedSourceProperties.toFeedSourceConfigs()).thenReturn(competitorConfigs());

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
        when(feedSourceProperties.toFeedSourceConfigs()).thenReturn(competitorConfigs());

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
    void getVendors_availableVendorsPopulatedFromCompetitorFeeds() throws Exception {
        when(feedSourceProperties.toFeedSourceConfigs()).thenReturn(competitorConfigs());

        mockMvc.perform(get("/research/vendors"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("availableVendors"));
    }

    @Test
    void getVendors_vendorCheckboxesTakePriorityOverFreeFormQuery() throws Exception {
        when(feedSourceProperties.toFeedSourceConfigs()).thenReturn(competitorConfigs());

        VendorCompareResult result = new VendorCompareResult(
                Collections.emptyList(), Collections.emptyList());
        when(compareVendorsUseCase.compareSelected(anyList(), nullable(String.class), anyInt(), anyString()))
                .thenReturn(result);

        // Both vendors param and query param present — vendors should take priority
        mockMvc.perform(get("/research/vendors")
                        .param("vendors", "Anthropic Healthcare")
                        .param("query", "AI diagnostics"))
                .andExpect(status().isOk());

        verify(compareVendorsUseCase).compareSelected(anyList(), nullable(String.class), anyInt(), anyString());
        verify(compareVendorsUseCase, never()).compare(anyString(), anyInt(), anyInt(), anyString());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<FeedSourceConfig> competitorConfigs() {
        return List.of(
                new FeedSourceConfig(1L, "Anthropic Healthcare AI", "Anthropic Healthcare",
                        "https://anthropic.com/news", FeedSourceConfig.FeedTier.COMPETITOR,
                        0.7, 5, List.of()),
                new FeedSourceConfig(1L, "OpenAI for Healthcare", "OpenAI Healthcare",
                        "https://openai.com/healthcare", FeedSourceConfig.FeedTier.COMPETITOR,
                        0.7, 5, List.of()),
                new FeedSourceConfig(1L, "Google Health AI", "Google Healthcare",
                        "https://ai.google/health", FeedSourceConfig.FeedTier.COMPETITOR,
                        0.7, 5, List.of()),
                // Two entries with same topic should yield one checkbox
                new FeedSourceConfig(1L, "Anthropic General News", "Anthropic Healthcare",
                        "https://anthropic.com/news/general", FeedSourceConfig.FeedTier.COMPETITOR,
                        0.65, 5, List.of("healthcare")),
                // Non-COMPETITOR feed should NOT appear as a vendor option
                new FeedSourceConfig(1L, "PubMed AI", "General AI Healthcare News",
                        "https://pubmed.com/rss", FeedSourceConfig.FeedTier.ACADEMIC,
                        0.9, 50, List.of())
        );
    }
}
