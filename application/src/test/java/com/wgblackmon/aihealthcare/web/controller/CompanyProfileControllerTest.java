package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyDiscoveryResult;
import com.wgblackmon.aihealthcare.domain.model.CompanyEvent;
import com.wgblackmon.aihealthcare.domain.model.CompanyEventType;
import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.CompanyTags;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryOutcomeStatus;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.DiscoverCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import com.wgblackmon.aihealthcare.domain.service.CompanyProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link CompanyProfileController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-30
 */
@WebMvcTest(CompanyProfileController.class)
class CompanyProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyProfilePort companyProfilePort;

    @MockitoBean
    private CompanyEventPort companyEventPort;

    @MockitoBean
    private DiscoverCompaniesUseCase discoverCompaniesUseCase;

    @MockitoBean
    private CompanyProfileService companyProfileService;

    @MockitoBean
    private NewsArticleRepository newsArticleRepository;

    @MockitoBean
    private RegulatoryEventPort regulatoryEventPort;

    @Test
    @WithMockUser
    void indexRendersCompanyIndexWithProfiles() throws Exception {
        Instant now = Instant.now();
        CompanyProfile profile = new CompanyProfile("tempus-ai", "Tempus AI",
                "https://tempus.com", "Clinical data", List.of("imaging"),
                List.of("a1"), now, now, 1, TrendDirection.RISING);

        when(companyProfilePort.findAll()).thenReturn(List.of(profile));
        when(newsArticleRepository.findRealArticlesByCompanyName("Tempus AI")).thenReturn(List.of());
        when(regulatoryEventPort.findByApplicant("Tempus AI", 100)).thenReturn(List.of());

        mockMvc.perform(get("/companies"))
                .andExpect(status().isOk())
                .andExpect(view().name("company-index"))
                .andExpect(model().attributeExists("profiles", "realArticleCounts", "regulatoryCounts"))
                .andExpect(model().attribute("profileCount", 1));
    }

    @Test
    @WithMockUser
    void indexRendersEmptyWhenNoProfiles() throws Exception {
        when(companyProfilePort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/companies"))
                .andExpect(status().isOk())
                .andExpect(view().name("company-index"))
                .andExpect(model().attribute("profileCount", 0));
    }

    @Test
    @WithMockUser
    void detailRendersProfileWithEvents() throws Exception {
        Instant now = Instant.now();
        CompanyProfile profile = new CompanyProfile("tempus-ai", "Tempus AI",
                "https://tempus.com", "Clinical data", List.of("imaging"),
                List.of("a1"), now, now, 1, TrendDirection.RISING);
        CompanyEvent event = new CompanyEvent("e1", "tempus-ai",
                CompanyEventType.FUNDING, "Tempus raises $200M",
                "Funding detected", "a1", now, now);

        NewsArticleEntity articleEntity = new NewsArticleEntity();
        articleEntity.setArticleId("real-1");
        articleEntity.setTitle("Tempus AI raises $200M in Series G");
        articleEntity.setUrl("https://example.com/tempus-funding");
        articleEntity.setBodyText("Tempus AI announced a $200M funding round.");
        articleEntity.setTopic("Healthcare AI");
        articleEntity.setSourceName("TechCrunch");
        articleEntity.setSourceTier("INDUSTRY");
        articleEntity.setSourceWeight(0.7);
        articleEntity.setPublishedAt(now);

        when(companyProfilePort.findBySlug("tempus-ai")).thenReturn(Optional.of(profile));
        when(companyEventPort.findByCompanySlug("tempus-ai")).thenReturn(List.of(event));
        when(newsArticleRepository.findRealArticlesByCompanyName("Tempus AI")).thenReturn(List.of(articleEntity));
        when(regulatoryEventPort.findByApplicant("Tempus AI", 50)).thenReturn(List.of());

        mockMvc.perform(get("/companies/tempus-ai"))
                .andExpect(status().isOk())
                .andExpect(view().name("company-detail"))
                .andExpect(model().attributeExists("profile", "events", "linkedArticles", "regulatoryEvents"));
    }

    @Test
    @WithMockUser
    void detailRedirectsWhenSlugNotFound() throws Exception {
        when(companyProfilePort.findBySlug("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/companies/nonexistent"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/companies"));
    }

    @Test
    @WithMockUser
    void detailRendersWithNoEvents() throws Exception {
        Instant now = Instant.now();
        CompanyProfile profile = new CompanyProfile("aidoc", "Aidoc",
                "https://aidoc.com", "Radiology AI", List.of(),
                List.of(), now, now, 0, TrendDirection.NEW);

        when(companyProfilePort.findBySlug("aidoc")).thenReturn(Optional.of(profile));
        when(companyEventPort.findByCompanySlug("aidoc")).thenReturn(List.of());
        when(regulatoryEventPort.findByApplicant("Aidoc", 50)).thenReturn(List.of());

        mockMvc.perform(get("/companies/aidoc"))
                .andExpect(status().isOk())
                .andExpect(view().name("company-detail"))
                .andExpect(model().attributeExists("events", "regulatoryEvents"));
    }

    @Test
    @WithMockUser
    void runDiscoveryCreatesProfilesAndRedirects() throws Exception {
        Company company = new Company("Tempus AI", "YC", "https://yc.com/tempus",
                "https://tempus.com", "Clinical data", CompanyTags.none(), true, true);
        CompanyDiscoveryResult result = new CompanyDiscoveryResult(
                List.of(company), "markdown", 10, 5, 3);

        when(discoverCompaniesUseCase.discover()).thenReturn(result);
        when(companyProfileService.toSlug("Tempus AI")).thenReturn("tempus-ai");
        when(companyProfilePort.findBySlug("tempus-ai")).thenReturn(Optional.empty());
        when(companyProfileService.upsertFromDiscovery(any(), anyList(), any()))
                .thenReturn(new CompanyProfile("tempus-ai", "Tempus AI", "https://tempus.com",
                        "Clinical data", List.of(), List.of(), Instant.now(), Instant.now(), 1, TrendDirection.NEW));
        when(companyProfileService.detectEvents(any(), anyList())).thenReturn(List.of());

        mockMvc.perform(post("/companies/discover").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/companies"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(companyProfilePort).save(any(CompanyProfile.class));
    }

    @Test
    void indexReturns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/companies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void detailRendersRegulatoryEventsWhenPresent() throws Exception {
        Instant now = Instant.now();
        CompanyProfile profile = new CompanyProfile("heartech", "HeartTech Inc",
                "https://hearttech.com", "AI ECG devices", List.of("cardiology"),
                List.of(), now, now, 0, TrendDirection.RISING);

        RegulatoryEvent regEvent = new RegulatoryEvent(
                "reg-001", RegulatoryEventType.FDA_510K_CLEARANCE, RegulatoryBody.FDA,
                "AI ECG Monitor Cleared", "FDA clears AI ECG monitor",
                "K241234", "HeartTech Inc", "AI ECG Monitor",
                "https://fda.gov/510k/K241234", null, now, now,
                List.of("AI", "ECG"),
                RegulatoryOutcomeStatus.CLEARED, now, "Traditional 510(k)", null);

        when(companyProfilePort.findBySlug("heartech")).thenReturn(Optional.of(profile));
        when(companyEventPort.findByCompanySlug("heartech")).thenReturn(List.of());
        when(newsArticleRepository.findRealArticlesByCompanyName("HeartTech Inc")).thenReturn(List.of());
        when(regulatoryEventPort.findByApplicant("HeartTech Inc", 50)).thenReturn(List.of(regEvent));

        mockMvc.perform(get("/companies/heartech"))
                .andExpect(status().isOk())
                .andExpect(view().name("company-detail"))
                .andExpect(model().attributeExists("regulatoryEvents", "regEventDates"));
    }

    @Test
    @WithMockUser
    void indexShowsRegulatoryCountBadges() throws Exception {
        Instant now = Instant.now();
        CompanyProfile profile = new CompanyProfile("heartech", "HeartTech Inc",
                "https://hearttech.com", "AI ECG devices", List.of("cardiology"),
                List.of(), now, now, 0, TrendDirection.RISING);

        RegulatoryEvent regEvent = new RegulatoryEvent(
                "reg-001", RegulatoryEventType.FDA_510K_CLEARANCE, RegulatoryBody.FDA,
                "AI ECG Monitor Cleared", "Summary",
                "K241234", "HeartTech Inc", "AI ECG Monitor",
                "https://fda.gov/510k/K241234", null, now, now,
                List.of("AI"),
                RegulatoryOutcomeStatus.CLEARED, now, null, null);

        when(companyProfilePort.findAll()).thenReturn(List.of(profile));
        when(newsArticleRepository.findRealArticlesByCompanyName("HeartTech Inc")).thenReturn(List.of());
        when(regulatoryEventPort.findByApplicant("HeartTech Inc", 100)).thenReturn(List.of(regEvent));

        mockMvc.perform(get("/companies"))
                .andExpect(status().isOk())
                .andExpect(view().name("company-index"))
                .andExpect(model().attributeExists("regulatoryCounts"));
    }

    @Test
    @WithMockUser
    void detailRendersWithNoRegulatoryEvents() throws Exception {
        Instant now = Instant.now();
        CompanyProfile profile = new CompanyProfile("startup-x", "Startup X",
                null, "New AI startup", List.of(),
                List.of(), now, now, 0, TrendDirection.NEW);

        when(companyProfilePort.findBySlug("startup-x")).thenReturn(Optional.of(profile));
        when(companyEventPort.findByCompanySlug("startup-x")).thenReturn(List.of());
        when(newsArticleRepository.findRealArticlesByCompanyName("Startup X")).thenReturn(List.of());
        when(regulatoryEventPort.findByApplicant("Startup X", 50)).thenReturn(List.of());

        mockMvc.perform(get("/companies/startup-x"))
                .andExpect(status().isOk())
                .andExpect(view().name("company-detail"))
                .andExpect(model().attributeExists("regulatoryEvents"));
    }
}
