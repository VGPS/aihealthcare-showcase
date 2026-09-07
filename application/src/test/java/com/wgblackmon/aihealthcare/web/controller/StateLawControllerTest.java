package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.LawChangeEvent;
import com.wgblackmon.aihealthcare.domain.model.LawSource;
import com.wgblackmon.aihealthcare.domain.model.LawStatus;
import com.wgblackmon.aihealthcare.domain.model.SourceType;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageStateLawsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link StateLawController} (Thymeleaf legislation views).
 *
 * <p>The index page at {@code /legislation} is public (permitAll).
 * Detail, map, and upcoming pages at {@code /legislation/**} require
 * authentication. Tests verify routing, model attributes, and filter
 * delegation to the use case.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
@WebMvcTest(StateLawController.class)
class StateLawControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageStateLawsUseCase legislationUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    @WithMockUser
    void legislation_index_returnsOk() throws Exception {
        when(legislationUseCase.getAll()).thenReturn(List.of(createTestLaw()));

        mockMvc.perform(get("/legislation"))
                .andExpect(status().isOk())
                .andExpect(view().name("legislation-index"))
                .andExpect(model().attributeExists("laws"))
                .andExpect(model().attribute("totalLaws", 1));
    }

    @Test
    @WithMockUser
    void legislation_index_withStateFilter_usesStatePort() throws Exception {
        when(legislationUseCase.getByState(StateCode.CA)).thenReturn(List.of(createTestLaw()));

        mockMvc.perform(get("/legislation").param("state", "CA"))
                .andExpect(status().isOk())
                .andExpect(view().name("legislation-index"));

        verify(legislationUseCase).getByState(StateCode.CA);
    }

    @Test
    @WithMockUser
    void legislation_index_withCategoryFilter_usesCategoryPort() throws Exception {
        when(legislationUseCase.getByCategory(LawCategory.PAYER_UTILIZATION_REVIEW))
                .thenReturn(List.of(createTestLaw()));

        mockMvc.perform(get("/legislation").param("category", "PAYER_UTILIZATION_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(view().name("legislation-index"));

        verify(legislationUseCase).getByCategory(LawCategory.PAYER_UTILIZATION_REVIEW);
    }

    @Test
    @WithMockUser
    void legislation_index_withSearchQuery() throws Exception {
        when(legislationUseCase.search("insurance")).thenReturn(List.of(createTestLaw()));

        mockMvc.perform(get("/legislation").param("q", "insurance"))
                .andExpect(status().isOk())
                .andExpect(view().name("legislation-index"))
                .andExpect(model().attribute("searchQuery", "insurance"));

        verify(legislationUseCase).search("insurance");
    }

    @Test
    @WithMockUser
    void legislation_detail_authenticated_returnsOk() throws Exception {
        when(legislationUseCase.getById("ca-ab-3030")).thenReturn(Optional.of(createTestLaw()));

        mockMvc.perform(get("/legislation/ca-ab-3030"))
                .andExpect(status().isOk())
                .andExpect(view().name("legislation-detail"))
                .andExpect(model().attributeExists("law"))
                .andExpect(model().attributeExists("formattedDates"));
    }

    @Test
    @WithMockUser
    void legislation_detail_notFound_redirectsToIndex() throws Exception {
        when(legislationUseCase.getById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/legislation/nonexistent"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void legislation_detail_requiresAuth() throws Exception {
        mockMvc.perform(get("/legislation/ca-ab-3030"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void legislation_map_authenticated_returnsOk() throws Exception {
        when(legislationUseCase.getAll()).thenReturn(List.of(createTestLaw()));

        mockMvc.perform(get("/legislation/map"))
                .andExpect(status().isOk())
                .andExpect(view().name("legislation-map"))
                .andExpect(model().attributeExists("lawsByState"))
                .andExpect(model().attributeExists("stateNames"))
                .andExpect(model().attribute("totalLaws", 1));
    }

    @Test
    @WithMockUser
    void legislation_upcoming_authenticated_returnsOk() throws Exception {
        when(legislationUseCase.getUpcoming(90)).thenReturn(List.of());

        mockMvc.perform(get("/legislation/upcoming"))
                .andExpect(status().isOk())
                .andExpect(view().name("legislation-upcoming"))
                .andExpect(model().attributeExists("upcomingLaws"))
                .andExpect(model().attributeExists("formattedDates"));
    }

    @Test
    @WithMockUser
    void legislation_index_withStatusFilter_usesStatusPort() throws Exception {
        when(legislationUseCase.getByStatus(LawStatus.ENACTED))
                .thenReturn(List.of(createTestLaw()));

        mockMvc.perform(get("/legislation").param("status", "ENACTED"))
                .andExpect(status().isOk())
                .andExpect(view().name("legislation-index"));

        verify(legislationUseCase).getByStatus(LawStatus.ENACTED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void legislation_changes_admin_returnsOk() throws Exception {
        LawChangeEvent event = new LawChangeEvent(1L, "ca-ab-3030",
                Instant.parse("2026-09-07T10:30:00Z"), "CONTENT_CHANGED",
                "Source content hash changed", false);
        when(legislationUseCase.getUnreviewedChanges()).thenReturn(List.of(event));

        mockMvc.perform(get("/legislation/changes"))
                .andExpect(status().isOk())
                .andExpect(view().name("legislation-changes"))
                .andExpect(model().attributeExists("changes"))
                .andExpect(model().attribute("changeCount", 1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void legislation_reviewChange_admin_redirects() throws Exception {
        mockMvc.perform(post("/legislation/changes/42/review")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/legislation/changes"));

        verify(legislationUseCase).reviewChange(42L);
    }

    // --- Helper ---

    private StateLaw createTestLaw() {
        return new StateLaw("ca-ab-3030", StateCode.CA, "California", "AB 3030",
                "Health care services: artificial intelligence", 2024,
                "2024-09-28", null, "2025-01-01", null,
                LawStatus.ENACTED, "Enacted",
                List.of(LawCategory.PROVIDER_DISCLOSURE_CONSENT),
                "Health care providers", "Must disclose AI use", "California DPA",
                List.of(new LawSource(SourceType.OFFICIAL,
                        "https://leginfo.legislature.ca.gov",
                        null, null, null, false)),
                null, "2026-09-06",
                Instant.parse("2026-09-06T00:00:00Z"),
                Instant.parse("2026-09-06T00:00:00Z"));
    }
}
