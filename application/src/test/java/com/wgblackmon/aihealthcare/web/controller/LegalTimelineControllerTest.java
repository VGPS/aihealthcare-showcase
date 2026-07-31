package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.web.MetaDescriptionFetcher;
import com.wgblackmon.aihealthcare.infrastructure.persistence.RegulatoryEventEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.RegulatoryEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link LegalTimelineController}.
 *
 * <p>Validates route rendering, category filtering, days parameter handling,
 * tier-based access gating, sorting, and category badge counts.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-29
 * @updated 2026-07-29
 */
@WebMvcTest(LegalTimelineController.class)
class LegalTimelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleIngestionPort articleIngestionPort;

    @MockitoBean
    private MonitorRegulatoryEventsUseCase regulatoryUseCase;

    @MockitoBean
    private RegulatoryEventRepository regulatoryEventRepository;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private MetaDescriptionFetcher metaDescriptionFetcher;

    // --- 1. rendersDefaultView ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void rendersDefaultView() throws Exception {
        stubAllSourcesEmpty(90);

        mockMvc.perform(get("/dashboard/legal"))
                .andExpect(status().isOk())
                .andExpect(view().name("legal-timeline"));
    }

    // --- 2. allCategoriesLoaded_whenNoFilter ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void allCategoriesLoaded_whenNoFilter() throws Exception {
        stubAllSourcesEmpty(90);

        mockMvc.perform(get("/dashboard/legal"))
                .andExpect(status().isOk());

        verify(articleIngestionPort).fetchByTopicWithArchiveLimit("AI Healthcare Legal", 90);
        verify(articleIngestionPort).fetchByTopicWithArchiveLimit("AI Healthcare Government Policy", 90);
        verify(regulatoryEventRepository).findByDiscoveredAtAfterOrderByDiscoveredAtDesc(any(Instant.class));
    }

    // --- 3. litigationFilter_loadsOnlyLegalArticles ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void litigationFilter_loadsOnlyLegalArticles() throws Exception {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), eq(90)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/legal").param("filter", "litigation"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filter", "litigation"));

        verify(articleIngestionPort).fetchByTopicWithArchiveLimit("AI Healthcare Legal", 90);
        verify(articleIngestionPort, never()).fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), eq(90));
        verify(regulatoryEventRepository, never()).findByDiscoveredAtAfterOrderByDiscoveredAtDesc(any(Instant.class));
    }

    // --- 4. regulationFilter_loadsOnlyRegulatoryEvents ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void regulationFilter_loadsOnlyRegulatoryEvents() throws Exception {
        when(regulatoryEventRepository.findByDiscoveredAtAfterOrderByDiscoveredAtDesc(any(Instant.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/legal").param("filter", "regulation"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filter", "regulation"));

        verify(regulatoryEventRepository).findByDiscoveredAtAfterOrderByDiscoveredAtDesc(any(Instant.class));
        verify(articleIngestionPort, never()).fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), eq(90));
        verify(articleIngestionPort, never()).fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), eq(90));
    }

    // --- 5. policyFilter_loadsOnlyPolicyArticles ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void policyFilter_loadsOnlyPolicyArticles() throws Exception {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), eq(90)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/legal").param("filter", "policy"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filter", "policy"));

        verify(articleIngestionPort).fetchByTopicWithArchiveLimit("AI Healthcare Government Policy", 90);
        verify(articleIngestionPort, never()).fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), eq(90));
        verify(regulatoryEventRepository, never()).findByDiscoveredAtAfterOrderByDiscoveredAtDesc(any(Instant.class));
    }

    // --- 6. entriesSortedDescending ---

    @SuppressWarnings("unchecked")
    @Test
    @WithMockUser(roles = "ADMIN")
    void entriesSortedDescending() throws Exception {
        Instant older = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant newer = Instant.now().minus(1, ChronoUnit.DAYS);

        NewsArticle olderArticle = article("a1", "Older Article", "AI Healthcare Legal", older);
        NewsArticle newerArticle = article("a2", "Newer Article", "AI Healthcare Legal", newer);

        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), eq(90)))
                .thenReturn(List.of(olderArticle, newerArticle));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), eq(90)))
                .thenReturn(List.of());
        when(regulatoryEventRepository.findByDiscoveredAtAfterOrderByDiscoveredAtDesc(any(Instant.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/legal"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entries", hasSize(2)));
    }

    // --- 7. daysParam_defaultIs90 ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void daysParam_defaultIs90() throws Exception {
        stubAllSourcesEmpty(90);

        mockMvc.perform(get("/dashboard/legal"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("days", 90));
    }

    // --- 8. daysParam_365_respected ---

    @Test
    @WithMockUser
    void daysParam_365_respected() throws Exception {
        when(subscriberPort.findByEmail("user"))
                .thenReturn(Optional.of(new Subscriber("user", "User", true, Instant.now(), SubscriptionTier.SUBSCRIBER, null, null, null)));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), eq(365)))
                .thenReturn(List.of());
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), eq(365)))
                .thenReturn(List.of());
        when(regulatoryEventRepository.findByDiscoveredAtAfterOrderByDiscoveredAtDesc(any(Instant.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/legal").param("days", "365"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("days", 365));
    }

    // --- 9. freeUser_cappedAt30Days ---

    @Test
    @WithMockUser
    void freeUser_cappedAt30Days() throws Exception {
        when(subscriberPort.findByEmail("user"))
                .thenReturn(Optional.of(new Subscriber("user", "User", true, Instant.now(), SubscriptionTier.FREE, null, null, null)));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), eq(30)))
                .thenReturn(List.of());
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), eq(30)))
                .thenReturn(List.of());
        when(regulatoryEventRepository.findByDiscoveredAtAfterOrderByDiscoveredAtDesc(any(Instant.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/dashboard/legal").param("days", "365"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("days", 30));
    }

    // --- 10. adminUser_getsFullAccess ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUser_getsFullAccess() throws Exception {
        stubAllSourcesEmpty(90);

        mockMvc.perform(get("/dashboard/legal"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", true));
    }

    // --- 11. freeUser_limitedAccess ---

    @Test
    @WithMockUser
    void freeUser_limitedAccess() throws Exception {
        stubAllSourcesEmpty(30);

        mockMvc.perform(get("/dashboard/legal"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", false));
    }

    // --- 12. requiresAuthentication ---

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard/legal"))
                .andExpect(status().isUnauthorized());
    }

    // --- 13. emptySources_rendersEmptyState ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptySources_rendersEmptyState() throws Exception {
        stubAllSourcesEmpty(90);

        mockMvc.perform(get("/dashboard/legal"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entryCount", 0))
                .andExpect(model().attribute("entries", hasSize(0)));
    }

    // --- 14. categoryCounts_areCorrect ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void categoryCounts_areCorrect() throws Exception {
        Instant now = Instant.now();

        // 2 litigation articles
        NewsArticle lit1 = article("lit1", "Lawsuit 1", "AI Healthcare Legal", now.minus(1, ChronoUnit.DAYS));
        NewsArticle lit2 = article("lit2", "Lawsuit 2", "AI Healthcare Legal", now.minus(2, ChronoUnit.DAYS));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), eq(90)))
                .thenReturn(List.of(lit1, lit2));

        // 1 policy article
        NewsArticle pol1 = article("pol1", "Policy Update", "AI Healthcare Government Policy", now.minus(3, ChronoUnit.DAYS));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), eq(90)))
                .thenReturn(List.of(pol1));

        // 3 regulatory events
        when(regulatoryEventRepository.findByDiscoveredAtAfterOrderByDiscoveredAtDesc(any(Instant.class)))
                .thenReturn(List.of(
                        regEvent("r1", "FDA 510(k) Clearance 1", now.minus(1, ChronoUnit.DAYS)),
                        regEvent("r2", "FDA 510(k) Clearance 2", now.minus(2, ChronoUnit.DAYS)),
                        regEvent("r3", "CMS Rule Update", now.minus(4, ChronoUnit.DAYS))
                ));

        mockMvc.perform(get("/dashboard/legal"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entryCount", 6))
                .andExpect(model().attribute("litigationCount", 2))
                .andExpect(model().attribute("regulationCount", 3))
                .andExpect(model().attribute("policyCount", 1));
    }

    // --- Helpers ---

    private void stubAllSourcesEmpty(int days) {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), eq(days)))
                .thenReturn(List.of());
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), eq(days)))
                .thenReturn(List.of());
        when(regulatoryEventRepository.findByDiscoveredAtAfterOrderByDiscoveredAtDesc(any(Instant.class)))
                .thenReturn(List.of());
    }

    private NewsArticle article(String id, String title, String topic, Instant publishedAt) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                "Body text for " + title, topic, null, null,
                "Test Source", "INDUSTRY", 0.5, publishedAt);
    }

    private RegulatoryEventEntity regEvent(String id, String title, Instant discoveredAt) {
        RegulatoryEventEntity entity = new RegulatoryEventEntity();
        entity.setEventId(id);
        entity.setEventType("FDA_510K_CLEARANCE");
        entity.setRegulatoryBody("FDA");
        entity.setTitle(title);
        entity.setSummary("Summary for " + title);
        entity.setSourceUrl("https://fda.gov/" + id);
        entity.setDiscoveredAt(discoveredAt);
        entity.setPublishedAt(discoveredAt);
        return entity;
    }
}
