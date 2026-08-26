package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.port.inbound.BrowseCompaniesUseCase;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for {@link PublicCompanyController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-26
 * @updated 2026-08-26
 */
@WebMvcTest(PublicCompanyController.class)
@Import(SecurityConfig.class)
class PublicCompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrowseCompaniesUseCase browseCompaniesUseCase;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    void directory_returnsOkWithCompanies() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(sampleCompany()));

        mockMvc.perform(get("/directory"))
               .andExpect(status().isOk())
               .andExpect(view().name("company-directory"))
               .andExpect(model().attributeExists("companies"))
               .andExpect(model().attribute("totalCount", 1));
    }

    @Test
    void directory_emptyList_returnsOk() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of());

        mockMvc.perform(get("/directory"))
               .andExpect(status().isOk())
               .andExpect(view().name("company-directory"))
               .andExpect(model().attribute("totalCount", 0));
    }

    @Test
    void directory_sectorFilter_passesDownstream() throws Exception {
        HealthcareAiCompany company = sampleCompany();
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(company));

        mockMvc.perform(get("/directory").param("sector", "Healthcare AI"))
               .andExpect(status().isOk())
               .andExpect(view().name("company-directory"))
               .andExpect(model().attributeExists("selectedSector"));
    }

    @Test
    void directory_sectorFilter_excludesNonMatchingCompanies() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(sampleCompany()));

        mockMvc.perform(get("/directory").param("sector", "Other Sector"))
               .andExpect(status().isOk())
               .andExpect(model().attribute("totalCount", 1)); // totalCount is unfiltered
    }

    @Test
    void detail_foundCompany_returnsDetailView() throws Exception {
        when(browseCompaniesUseCase.getCompany("grelin-health")).thenReturn(Optional.of(sampleCompany()));

        mockMvc.perform(get("/directory/grelin-health"))
               .andExpect(status().isOk())
               .andExpect(view().name("company-directory-detail"))
               .andExpect(model().attributeExists("company"))
               .andExpect(model().attribute("slug", "grelin-health"));
    }

    @Test
    void detail_notFound_redirectsToDirectory() throws Exception {
        when(browseCompaniesUseCase.getCompany("no-such-co")).thenReturn(Optional.empty());

        mockMvc.perform(get("/directory/no-such-co"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/directory"));
    }

    @Test
    void buildDescriptionHtml_convertsCitationMarkersToAnchorLinks() {
        String html = PublicCompanyController.buildDescriptionHtml(
                "AI-powered platform [1] built on research [2].");
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("<a href=\"#source-1\"")
                .contains("<a href=\"#source-2\"")
                .contains("[1]")
                .contains("[2]")
                .doesNotContain("<p");
    }

    @Test
    void buildDescriptionHtml_escapesHtmlBeforeConvertingLinks() {
        String html = PublicCompanyController.buildDescriptionHtml("A & B <test> [1]");
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("&amp;")
                .contains("&lt;test&gt;")
                .contains("<a href=\"#source-1\"");
    }

    @Test
    void buildDescriptionHtml_returnsNullForNullInput() {
        org.assertj.core.api.Assertions.assertThat(
                PublicCompanyController.buildDescriptionHtml(null)).isNull();
    }

    @Test
    void toSlug_convertsNameCorrectly() {
        org.assertj.core.api.Assertions.assertThat(PublicCompanyController.toSlug("Grelin Health"))
                .isEqualTo("grelin-health");
        org.assertj.core.api.Assertions.assertThat(PublicCompanyController.toSlug("Tempus AI"))
                .isEqualTo("tempus-ai");
        org.assertj.core.api.Assertions.assertThat(PublicCompanyController.toSlug(null))
                .isEqualTo("");
    }

    private HealthcareAiCompany sampleCompany() {
        return new HealthcareAiCompany(
                "grelin-id", "Grelin Health", "grelin health", "grelinhealth.com",
                "AI-powered healthcare analytics", "Austin, TX", 2021,
                "Healthcare AI", "clinical analytics", "Seed", "$5M", null,
                List.of("https://perplexity.ai/sources/grelin"),
                false, List.of(),
                Instant.parse("2026-08-01T00:00:00Z"), null);
    }
}
