package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.TrackFrontierClaimsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link ClaimTrackerRestController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
@WebMvcTest(ClaimTrackerRestController.class)
class ClaimTrackerRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackFrontierClaimsUseCase claimsUseCase;

    @MockitoBean
    private ArticleIngestionPort ingestionPort;

    @MockitoBean
    private NewsArticleRepository articleRepository;

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    @WithMockUser
    void getAll_returns200WithClaimsList() throws Exception {
        when(claimsUseCase.getAll()).thenReturn(List.of(buildClaim("id-1", "OpenAI")));

        mockMvc.perform(get("/api/v1/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].company").value("OpenAI"));
    }

    @Test
    @WithMockUser
    void getAll_companyParam_filtersResults() throws Exception {
        when(claimsUseCase.getByCompany("Meta")).thenReturn(List.of(buildClaim("id-2", "Meta")));

        mockMvc.perform(get("/api/v1/claims").param("company", "Meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company").value("Meta"));
    }

    @Test
    @WithMockUser
    void getById_existingId_returns200() throws Exception {
        when(claimsUseCase.getById("id-1")).thenReturn(Optional.of(buildClaim("id-1", "Google")));

        mockMvc.perform(get("/api/v1/claims/id-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value("id-1"));
    }

    @Test
    @WithMockUser
    void getById_missingId_returns404() throws Exception {
        when(claimsUseCase.getById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/claims/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getAll_emptyResults_returns200EmptyArray() throws Exception {
        when(claimsUseCase.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private FrontierClaim buildClaim(String id, String company) {
        return new FrontierClaim(id, company, "Claim text here.",
                null, "https://example.com", "Source",
                ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE,
                "No evidence cited.", "art-1", NOW, null);
    }
}
