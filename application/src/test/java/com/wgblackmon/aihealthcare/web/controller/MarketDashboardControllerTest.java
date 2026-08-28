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
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionQueryService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionSnapshot;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ReactionHorizon;
import com.wgblackmon.aihealthcare.domain.marketanalysis.RollupEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.WeeklyRollup;
import com.wgblackmon.aihealthcare.domain.marketanalysis.WeeklyRollupService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
 * @updated 2026-08-20 — added WeeklyRollupService mock + weekly endpoint tests
 */
@Import(SecurityConfig.class)
@WebMvcTest(MarketDashboardController.class)
class MarketDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketDigestService marketDigestService;

    @MockitoBean
    private WeeklyRollupService weeklyRollupService;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private PriceReactionQueryService priceReactionQueryService;

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
    @DisplayName("GET /dashboard/market includes a reaction badge when a snapshot exists")
    void marketLatest_withReactionSnapshot_includesReactionBadge() throws Exception {
        when(marketDigestService.findLatest()).thenReturn(Optional.of(digestWithEntries(NewsCategory.EARNINGS)));
        PriceReactionSnapshot snapshot = new PriceReactionSnapshot(
                "entry-1", "ACME", ReactionHorizon.ONE_DAY,
                new BigDecimal("10.00"), new BigDecimal("10.80"),
                new BigDecimal("8.00"), Instant.now());
        when(priceReactionQueryService.findReactions("ACME", Instant.parse("2026-08-19T10:00:00Z")))
                .thenReturn(List.of(snapshot));

        MvcResult result = mockMvc.perform(get("/dashboard/market"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) result.getModelAndView().getModel().get("entries");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reactions = (List<Map<String, Object>>) entries.get(0).get("reactions");

        assertThat(reactions).hasSize(1);
        assertThat(reactions.get(0).get("label")).isEqualTo("ACME 1d +8.00%");
        assertThat(reactions.get(0).get("positive")).isEqualTo(true);
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

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market/history with digests returns history view")
    void marketHistory_withDigests_returnsView() throws Exception {
        when(marketDigestService.findAll()).thenReturn(List.of(digestWithEntries(NewsCategory.EARNINGS)));

        mockMvc.perform(get("/dashboard/market/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("market-digest-history"))
                .andExpect(model().attribute("hasHistory", true))
                .andExpect(model().attribute("totalDigests", 1))
                .andExpect(model().attribute("selectedDays", 0));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market/history with no digests returns empty state")
    void marketHistory_noDigests_returnsEmptyState() throws Exception {
        when(marketDigestService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/market/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("market-digest-history"))
                .andExpect(model().attribute("hasHistory", false))
                .andExpect(model().attribute("totalDigests", 0));
    }

    @Test
    @DisplayName("GET /dashboard/market/history unauthenticated redirects to login")
    void marketHistory_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/dashboard/market/history"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market/history?days=7 filters digests to last 7 days")
    void marketHistory_withDaysParam_filtersResults() throws Exception {
        // Digest from today — should be included in a 7-day window
        MarketDigest fixedDateDigest = digestWithEntries(NewsCategory.EARNINGS);
        MarketDigest recent = new MarketDigest(
                LocalDate.now(), fixedDateDigest.entries(), fixedDateDigest.generatedAt());
        when(marketDigestService.findAll()).thenReturn(List.of(recent));

        mockMvc.perform(get("/dashboard/market/history").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(view().name("market-digest-history"))
                .andExpect(model().attribute("selectedDays", 7))
                .andExpect(model().attribute("filteredCount", 1));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market/weekly returns market-digest-weekly view")
    void weekly_returnsWeeklyView() throws Exception {
        LocalDate weekStart = LocalDate.of(2026, 8, 17);
        WeeklyRollup rollup = new WeeklyRollup(weekStart, List.of(), java.time.Instant.now());
        when(weeklyRollupService.buildRollup(weekStart)).thenReturn(rollup);

        mockMvc.perform(get("/dashboard/market/weekly").param("weekOf", "2026-08-17"))
                .andExpect(status().isOk())
                .andExpect(view().name("market-digest-weekly"))
                .andExpect(model().attribute("totalEntries", 0))
                .andExpect(model().attribute("activePage", "market-weekly"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market/weekly with entries populates model rows")
    void weekly_withEntries_populatesRows() throws Exception {
        LocalDate weekStart = LocalDate.of(2026, 8, 17);
        MarketDigestEntry entry = digestWithEntries(NewsCategory.EARNINGS).entries().get(0);
        RollupEntry rollupEntry = new RollupEntry(entry, entry.rank(), FactClassification.CONFIRMED, 2);
        WeeklyRollup rollup = new WeeklyRollup(weekStart, List.of(rollupEntry), java.time.Instant.now());
        when(weeklyRollupService.buildRollup(weekStart)).thenReturn(rollup);

        mockMvc.perform(get("/dashboard/market/weekly").param("weekOf", "2026-08-17"))
                .andExpect(status().isOk())
                .andExpect(view().name("market-digest-weekly"))
                .andExpect(model().attribute("totalEntries", 1));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dashboard/market/weekly includes a reaction badge when a snapshot exists")
    void weekly_withReactionSnapshot_includesReactionBadge() throws Exception {
        LocalDate weekStart = LocalDate.of(2026, 8, 17);
        MarketDigestEntry entry = digestWithEntries(NewsCategory.EARNINGS).entries().get(0);
        RollupEntry rollupEntry = new RollupEntry(entry, entry.rank(), FactClassification.CONFIRMED, 2);
        WeeklyRollup rollup = new WeeklyRollup(weekStart, List.of(rollupEntry), java.time.Instant.now());
        when(weeklyRollupService.buildRollup(weekStart)).thenReturn(rollup);

        PriceReactionSnapshot snapshot = new PriceReactionSnapshot(
                "entry-1", "ACME", ReactionHorizon.ONE_DAY,
                new BigDecimal("10.00"), new BigDecimal("10.80"),
                new BigDecimal("8.00"), Instant.now());
        when(priceReactionQueryService.findReactions("ACME", Instant.parse("2026-08-19T10:00:00Z")))
                .thenReturn(List.of(snapshot));

        MvcResult result = mockMvc.perform(get("/dashboard/market/weekly").param("weekOf", "2026-08-17"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) result.getModelAndView().getModel().get("entries");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reactions = (List<Map<String, Object>>) entries.get(0).get("reactions");

        assertThat(reactions).hasSize(1);
        assertThat(reactions.get(0).get("label")).isEqualTo("ACME 1d +8.00%");
    }
}
