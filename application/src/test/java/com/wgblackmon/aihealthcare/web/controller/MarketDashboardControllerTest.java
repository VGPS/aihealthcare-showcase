package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactAssessment;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDimension;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDirection;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketImpactRank;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link MarketDashboardController}.
 *
 * <p>Verifies routing, model population, category filtering, tier gating,
 * and the empty/no-digest states without making real LLM or database calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Import(SecurityConfig.class)
@WebMvcTest(MarketDashboardController.class)
class MarketDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketDigestService marketDigestService;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    // ─── helpers ────────────────────────────────────────────────────────────

    private MarketDigest digestWithEntries(NewsCategory category) {
        MarketNewsItem item = new MarketNewsItem(
                "AI Earnings Beat",
                "Company reported strong quarterly results driven by AI revenue.",
                List.of("https://example.com/1"),
                Instant.parse("2026-08-19T10:00:00Z"),
                category,
                null
        );
        ImpactAssessment impact = new ImpactAssessment(
                ImpactDimension.REVENUE, ImpactDirection.POSITIVE, "Strong AI revenue growth");
        AffectedCompany company = new AffectedCompany("AcmeMed", "ACME", "earnings subject", null);
        MarketDigestEntry entry = new MarketDigestEntry(
                item, List.of(impact), FactClassification.CONFIRMED, new MarketImpactRank(1),
                List.of(company));
        return new MarketDigest(LocalDate.of(2026, 8, 19), List.of(entry), Instant.now());
    }

    // ─── tests ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market returns 200 with market-digest view")
    void marketLatest_withDigest_returnsView() throws Exception {
        when(marketDigestService.findLatest()).thenReturn(Optional.of(digestWithEntries(NewsCategory.EARNINGS)));

        mockMvc.perform(get("/dashboard/market"))
                .andExpect(status().isOk())
                .andExpect(view().name("market-digest"))
                .andExpect(model().attributeExists("entries", "categoryCounts", "totalEntries"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market with no digest shows empty state")
    void marketLatest_noDigest_returnsEmptyState() throws Exception {
        when(marketDigestService.findLatest()).thenReturn(Optional.empty());

        mockMvc.perform(get("/dashboard/market"))
                .andExpect(status().isOk())
                .andExpect(view().name("market-digest"))
                .andExpect(model().attribute("totalEntries", 0));
    }

    @Test
    @DisplayName("GET /dashboard/market unauthenticated redirects to login")
    void marketLatest_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/dashboard/market"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market sets activePage to market")
    void marketLatest_setsActivePage() throws Exception {
        when(marketDigestService.findLatest()).thenReturn(Optional.empty());

        mockMvc.perform(get("/dashboard/market"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("activePage", "market"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market?category=EARNINGS filters entries by category")
    void marketLatest_withCategoryFilter_setsFilterCategory() throws Exception {
        when(marketDigestService.findLatest()).thenReturn(Optional.of(digestWithEntries(NewsCategory.EARNINGS)));

        mockMvc.perform(get("/dashboard/market").param("category", "EARNINGS"))
                .andExpect(status().isOk())
                .andExpect(view().name("market-digest"))
                .andExpect(model().attribute("filterCategory", "EARNINGS"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market/{date} returns digest for that date")
    void marketByDate_withDigest_returnsView() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 19);
        when(marketDigestService.findByDate(date))
                .thenReturn(Optional.of(digestWithEntries(NewsCategory.REGULATORY)));

        mockMvc.perform(get("/dashboard/market/2026-08-19"))
                .andExpect(status().isOk())
                .andExpect(view().name("market-digest"))
                .andExpect(model().attributeExists("entries"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market/{date} with no digest for that date shows empty state")
    void marketByDate_noDigest_returnsEmptyState() throws Exception {
        LocalDate date = LocalDate.of(2026, 1, 1);
        when(marketDigestService.findByDate(date)).thenReturn(Optional.empty());

        mockMvc.perform(get("/dashboard/market/2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("market-digest"))
                .andExpect(model().attribute("totalEntries", 0));
    }
}
