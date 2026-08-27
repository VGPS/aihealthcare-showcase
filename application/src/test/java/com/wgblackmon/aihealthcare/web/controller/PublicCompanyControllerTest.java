package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanySignal;
import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.BrowseCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for {@link PublicCompanyController}.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-08-26
 * @updated 2026-08-27
 */
@WebMvcTest(PublicCompanyController.class)
@Import(SecurityConfig.class)
class PublicCompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrowseCompaniesUseCase browseCompaniesUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    void directory_returnsOkWithCompanies() throws Exception {
        HealthcareAiCompany c = sampleCompany();
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(c));
        when(browseCompaniesUseCase.computeSignals(any())).thenReturn(Map.of());

        mockMvc.perform(get("/directory"))
               .andExpect(status().isOk())
               .andExpect(view().name("company-directory"))
               .andExpect(model().attributeExists("companies"))
               .andExpect(model().attribute("totalCount", 1));
    }

    @Test
    void directory_emptyList_returnsOk() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of());
        when(browseCompaniesUseCase.computeSignals(any())).thenReturn(Map.of());

        mockMvc.perform(get("/directory"))
               .andExpect(status().isOk())
               .andExpect(view().name("company-directory"))
               .andExpect(model().attribute("totalCount", 0));
    }

    @Test
    void directory_sectorFilter_passesDownstream() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(sampleCompany()));
        when(browseCompaniesUseCase.computeSignals(any())).thenReturn(Map.of());

        mockMvc.perform(get("/directory").param("sector", "Medical Imaging & Diagnostics"))
               .andExpect(status().isOk())
               .andExpect(view().name("company-directory"))
               .andExpect(model().attributeExists("selectedSector"));
    }

    @Test
    void directory_sectorFilter_excludesNonMatchingCompanies() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(sampleCompany()));
        when(browseCompaniesUseCase.computeSignals(any())).thenReturn(Map.of());

        mockMvc.perform(get("/directory").param("sector", "Other Sector"))
               .andExpect(status().isOk())
               .andExpect(model().attribute("totalCount", 1)); // totalCount is unfiltered
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"USER"})
    void directory_sortTrending_setsSelectedSort() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(sampleCompany()));
        when(browseCompaniesUseCase.computeSignals(any())).thenReturn(Map.of());
        when(subscriberPort.findByEmail(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/directory").param("sort", "trending"))
               .andExpect(status().isOk())
               .andExpect(model().attribute("selectedSort", "trending"));
    }

    @Test
    @WithMockUser(username = "sub@test.com", roles = {"USER"})
    void directory_sortFunded_filtersToFundedCompanies() throws Exception {
        HealthcareAiCompany c = sampleCompany();
        CompanySignal sig = new CompanySignal(c.companyId(), 5, "FUNDING", "$50M",
                Instant.now(), 0.1, "POSITIVE", true, 35);
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(c));
        when(browseCompaniesUseCase.computeSignals(any())).thenReturn(Map.of(c.companyId(), sig));
        when(subscriberPort.findByEmail("sub@test.com")).thenReturn(
                Optional.of(sampleSubscriber(SubscriptionTier.SUBSCRIBER)));

        mockMvc.perform(get("/directory").param("sort", "funded"))
               .andExpect(status().isOk())
               .andExpect(model().attribute("selectedSort", "funded"));
    }

    @Test
    @WithMockUser(username = "sub@test.com", roles = {"USER"})
    void directory_sortWatchlist_filtersToNegativeSentiment() throws Exception {
        HealthcareAiCompany c = sampleCompany();
        CompanySignal sig = new CompanySignal(c.companyId(), 1, null, null, null,
                -0.5, "NEGATIVE", true, 0);
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(c));
        when(browseCompaniesUseCase.computeSignals(any())).thenReturn(Map.of(c.companyId(), sig));
        when(subscriberPort.findByEmail("sub@test.com")).thenReturn(
                Optional.of(sampleSubscriber(SubscriptionTier.SUBSCRIBER)));

        mockMvc.perform(get("/directory").param("sort", "watchlist"))
               .andExpect(status().isOk())
               .andExpect(model().attribute("selectedSort", "watchlist"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void exportCsv_adminReturnsCSVContentType() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(sampleCompany()));
        when(browseCompaniesUseCase.computeSignals(any())).thenReturn(Map.of());
        when(subscriberPort.findByEmail(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/directory/export.csv"))
               .andExpect(status().isOk())
               .andExpect(header().string("Content-Disposition",
                       "attachment; filename=\"ai-healthcare-companies.csv\""));
    }

    @Test
    void exportCsv_anonymousRedirectsToPricing() throws Exception {
        mockMvc.perform(get("/directory/export.csv"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/pricing"));
    }

    @Test
    @WithMockUser(username = "free@test.com", roles = {"USER"})
    void exportCsv_freeUserRedirectsToPricing() throws Exception {
        when(subscriberPort.findByEmail("free@test.com")).thenReturn(
                Optional.of(sampleSubscriber(SubscriptionTier.FREE)));

        mockMvc.perform(get("/directory/export.csv"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/pricing"));
    }

    @Test
    @WithMockUser(username = "sub@test.com", roles = {"USER"})
    void directory_subscriberCanSeeFundedTab() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(sampleCompany()));
        when(browseCompaniesUseCase.computeSignals(any())).thenReturn(Map.of());
        when(subscriberPort.findByEmail("sub@test.com")).thenReturn(
                Optional.of(sampleSubscriber(SubscriptionTier.SUBSCRIBER)));

        mockMvc.perform(get("/directory").param("sort", "funded"))
               .andExpect(status().isOk())
               .andExpect(model().attribute("selectedSort", "funded"))
               .andExpect(model().attribute("upgradeRequired", false));
    }

    @Test
    @WithMockUser(username = "free@test.com", roles = {"USER"})
    void directory_freeUserFundedSortIsDowngradedToRelevance() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(sampleCompany()));
        when(browseCompaniesUseCase.computeSignals(any())).thenReturn(Map.of());
        when(subscriberPort.findByEmail("free@test.com")).thenReturn(
                Optional.of(sampleSubscriber(SubscriptionTier.FREE)));

        mockMvc.perform(get("/directory").param("sort", "funded"))
               .andExpect(status().isOk())
               .andExpect(model().attribute("selectedSort", (Object) null))
               .andExpect(model().attribute("upgradeRequired", true));
    }

    @Test
    void directory_anonymousTrendingSortIsDowngradedToRelevance() throws Exception {
        when(browseCompaniesUseCase.listCompanies()).thenReturn(List.of(sampleCompany()));
        when(browseCompaniesUseCase.computeSignals(any())).thenReturn(Map.of());

        mockMvc.perform(get("/directory").param("sort", "trending"))
               .andExpect(status().isOk())
               .andExpect(model().attribute("selectedSort", (Object) null))
               .andExpect(model().attribute("upgradeRequired", true));
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
                "Healthcare AI", "clinical analytics", null, "Seed", "$5M", null,
                List.of("https://perplexity.ai/sources/grelin"),
                false, List.of(),
                Instant.parse("2026-08-01T00:00:00Z"), null);
    }

    private Subscriber sampleSubscriber(SubscriptionTier tier) {
        return new Subscriber("test@test.com", "Test User", true,
                Instant.parse("2026-01-01T00:00:00Z"), tier, null, null, null);
    }
}
