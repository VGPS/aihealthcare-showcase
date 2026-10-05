package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.port.inbound.TrackFrontierClaimsUseCase;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link ClaimTrackerController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
@WebMvcTest(ClaimTrackerController.class)
class ClaimTrackerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackFrontierClaimsUseCase claimsUseCase;

    @MockitoBean
    private TierResolver tierResolver;

    @MockitoBean
    private NewsArticleRepository articleRepository;

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    @WithMockUser
    void claimTracker_renders200() throws Exception {
        when(claimsUseCase.getAll()).thenReturn(List.of());
        when(tierResolver.hasFullAccess(any())).thenReturn(false);

        mockMvc.perform(get("/dashboard/claims"))
                .andExpect(status().isOk())
                .andExpect(view().name("claim-tracker"))
                .andExpect(model().attributeExists("claims", "companies", "verdictCounts", "claimTypes", "verdicts"));
    }

    @Test
    @WithMockUser
    void claimTracker_companyFilter_callsGetByCompany() throws Exception {
        FrontierClaim claim = buildClaim("openai", "OpenAI", "Claim.", ClaimVerdict.MARKETING_HYPE);
        when(claimsUseCase.getByCompany("OpenAI")).thenReturn(List.of(claim));
        when(claimsUseCase.getAll()).thenReturn(List.of(claim));
        when(tierResolver.hasFullAccess(any())).thenReturn(true);

        mockMvc.perform(get("/dashboard/claims").param("company", "OpenAI"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterCompany", "OpenAI"));
    }

    @Test
    @WithMockUser
    void claimTracker_verdictFilter_callsGetByVerdict() throws Exception {
        FrontierClaim claim = buildClaim("id-1", "Google", "Gemini.", ClaimVerdict.MARKETING_HYPE);
        when(claimsUseCase.getByVerdict(ClaimVerdict.MARKETING_HYPE)).thenReturn(List.of(claim));
        when(claimsUseCase.getAll()).thenReturn(List.of(claim));
        when(tierResolver.hasFullAccess(any())).thenReturn(true);

        mockMvc.perform(get("/dashboard/claims").param("verdict", "MARKETING_HYPE"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterVerdict", "MARKETING_HYPE"));
    }

    @Test
    @WithMockUser
    void claimTracker_freeTierLimit_capsAtFiveResults() throws Exception {
        List<FrontierClaim> many = buildManyClaims(8);
        when(claimsUseCase.getAll()).thenReturn(many);
        when(tierResolver.hasFullAccess(any())).thenReturn(false);

        mockMvc.perform(get("/dashboard/claims"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("claims"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void claimTracker_adminFullAccess_returnsAll() throws Exception {
        List<FrontierClaim> many = buildManyClaims(8);
        when(claimsUseCase.getAll()).thenReturn(many);
        when(tierResolver.hasFullAccess(any())).thenReturn(true);

        mockMvc.perform(get("/dashboard/claims"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", true));
    }

    @Test
    void claimTracker_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/dashboard/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void claimTracker_emptyState_rendersWithoutError() throws Exception {
        when(claimsUseCase.getAll()).thenReturn(List.of());
        when(tierResolver.hasFullAccess(any())).thenReturn(true);

        mockMvc.perform(get("/dashboard/claims"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", true));
    }

    private FrontierClaim buildClaim(String id, String company, String text, ClaimVerdict verdict) {
        return new FrontierClaim(id, company, text, null, "https://example.com", "Source",
                ClaimType.CAPABILITY_CLAIM, verdict, "Notes.", "art-1", NOW, null);
    }

    private List<FrontierClaim> buildManyClaims(int count) {
        List<FrontierClaim> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(buildClaim("id-" + i, "Company-" + i, "Claim " + i + ".", ClaimVerdict.MARKETING_HYPE));
        }
        return list;
    }
}
