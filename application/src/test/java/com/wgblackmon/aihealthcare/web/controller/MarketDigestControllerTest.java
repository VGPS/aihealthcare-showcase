package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketImpactRank;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionQueryService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionSnapshot;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ReactionHorizon;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link MarketDigestController}.
 *
 * <p>Covers the three query endpoints: latest, by-date, and paginated list.
 * {@link MarketDigestService} is provided as a mock bean so no real pipeline
 * or database is involved.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@WebMvcTest(MarketDigestController.class)
class MarketDigestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDigestService marketDigestService;

    @MockBean
    private PriceReactionQueryService priceReactionQueryService;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 19);

    // ─── GET /api/market-digest/latest ──────────────────────────────────────

    @Test
    @WithMockUser
    void getLatest_whenDigestExists_returns200WithDate() throws Exception {
        when(marketDigestService.findLatest()).thenReturn(Optional.of(digestWithOneEntry()));

        mockMvc.perform(get("/api/market-digest/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-08-19"))
                .andExpect(jsonPath("$.entryCount").value(1))
                .andExpect(jsonPath("$.entries[0].headline").value("EARNINGS headline"));
    }

    @Test
    @WithMockUser
    void getLatest_withReactionSnapshot_includesReactionInResponse() throws Exception {
        Instant publishedAt = Instant.parse("2026-08-19T12:00:00Z");
        MarketNewsItem item = new MarketNewsItem(
                "EARNINGS headline", "Summary text.", List.of(), publishedAt, NewsCategory.EARNINGS, null);
        AffectedCompany company = new AffectedCompany("Doximity", "DOCS", "earnings subject", null);
        MarketDigestEntry entry = new MarketDigestEntry(
                item, List.of(), FactClassification.CONFIRMED, new MarketImpactRank(1), List.of(company));
        when(marketDigestService.findLatest())
                .thenReturn(Optional.of(new MarketDigest(DATE, List.of(entry), Instant.now())));

        PriceReactionSnapshot snapshot = new PriceReactionSnapshot(
                "entry-1", "DOCS", ReactionHorizon.ONE_DAY,
                new BigDecimal("10.00"), new BigDecimal("10.80"),
                new BigDecimal("8.00"), Instant.now());
        when(priceReactionQueryService.findReactions("DOCS", publishedAt)).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/api/market-digest/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].affectedCompanies[0].tickerSymbol").value("DOCS"))
                .andExpect(jsonPath("$.entries[0].affectedCompanies[0].reactions[0].horizon").value("ONE_DAY"))
                .andExpect(jsonPath("$.entries[0].affectedCompanies[0].reactions[0].pctChange").value(8.00));
    }

    @Test
    @WithMockUser
    void getLatest_whenNoDigest_returns404() throws Exception {
        when(marketDigestService.findLatest()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/market-digest/latest"))
                .andExpect(status().isNotFound());
    }

    // ─── GET /api/market-digest/{date} ──────────────────────────────────────

    @Test
    @WithMockUser
    void getByDate_whenFound_returns200() throws Exception {
        when(marketDigestService.findByDate(DATE)).thenReturn(Optional.of(digestWithOneEntry()));

        mockMvc.perform(get("/api/market-digest/2026-08-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-08-19"))
                .andExpect(jsonPath("$.entryCount").value(1));
    }

    @Test
    @WithMockUser
    void getByDate_whenNotFound_returns404() throws Exception {
        when(marketDigestService.findByDate(any(LocalDate.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/market-digest/2026-08-01"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getByDate_withInvalidFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/market-digest/not-a-date"))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/market-digest ──────────────────────────────────────────────

    @Test
    @WithMockUser
    void getAll_returns200WithSummaryList() throws Exception {
        when(marketDigestService.findAll()).thenReturn(List.of(
                MarketDigest.empty(LocalDate.of(2026, 8, 18)),
                MarketDigest.empty(LocalDate.of(2026, 8, 19))
        ));

        mockMvc.perform(get("/api/market-digest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].entryCount").value(0));
    }

    @Test
    @WithMockUser
    void getAll_withDateFilter_returnsOnlyMatchingDates() throws Exception {
        when(marketDigestService.findAll()).thenReturn(List.of(
                MarketDigest.empty(LocalDate.of(2026, 8, 17)),
                MarketDigest.empty(LocalDate.of(2026, 8, 18)),
                MarketDigest.empty(LocalDate.of(2026, 8, 19))
        ));

        mockMvc.perform(get("/api/market-digest")
                        .param("from", "2026-08-18")
                        .param("to", "2026-08-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-08-18"));
    }

    @Test
    void getLatest_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/market-digest/latest"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/market-digest/weekly-rollup ───────────────────────────────

    @Test
    @WithMockUser
    void getWeeklyRollup_returnsAggregatedResponse() throws Exception {
        LocalDate weekOf = LocalDate.of(2026, 8, 11);
        when(marketDigestService.findByDateRange(any(), any()))
                .thenReturn(List.of(digestWithOneEntry()));

        mockMvc.perform(get("/api/market-digest/weekly-rollup")
                        .param("weekOf", "2026-08-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekOf").value("2026-08-11"))
                .andExpect(jsonPath("$.weekEnd").value("2026-08-17"))
                .andExpect(jsonPath("$.totalQualifyingEntries").value(1))
                .andExpect(jsonPath("$.dailySummaries.length()").value(1));
    }

    @Test
    @WithMockUser
    void getWeeklyRollup_whenNoDigests_returnsEmptyRollup() throws Exception {
        when(marketDigestService.findByDateRange(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/market-digest/weekly-rollup")
                        .param("weekOf", "2026-08-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQualifyingEntries").value(0))
                .andExpect(jsonPath("$.dailySummaries.length()").value(0))
                .andExpect(jsonPath("$.byCategory.length()").value(0));
    }

    @Test
    @WithMockUser
    void getWeeklyRollup_missingWeekOf_returns400() throws Exception {
        mockMvc.perform(get("/api/market-digest/weekly-rollup"))
                .andExpect(status().isBadRequest());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private MarketDigest digestWithOneEntry() {
        MarketNewsItem item = new MarketNewsItem(
                "EARNINGS headline",
                "Summary text.",
                List.of(),
                Instant.parse("2026-08-19T12:00:00Z"),
                NewsCategory.EARNINGS,
                null
        );
        MarketDigestEntry entry = new MarketDigestEntry(
                item,
                List.of(),
                FactClassification.CONFIRMED,
                new MarketImpactRank(1),
                List.of()
        );
        return new MarketDigest(DATE, List.of(entry), Instant.now());
    }
}
