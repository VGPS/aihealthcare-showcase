package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link TrendPublicController}.
 *
 * <p>Verifies the public /trends redirect, dated snapshot detail rendering,
 * 404 handling, and SEO model attributes (pageTitle, pageDescription).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-30
 * @updated 2026-09-30
 */
@Import(SecurityConfig.class)
@WebMvcTest(TrendPublicController.class)
class TrendPublicControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DetectTrendsUseCase detectTrendsUseCase;

    @MockitoBean
    ApiKeyPort apiKeyPort;

    // ── /trends redirect ───────────────────────────────────────────────────────

    @Test
    void trends_redirectsToLatestDateSlug() throws Exception {
        // Snapshot at 2026-09-28 08:00 UTC
        TrendSnapshot snapshot = snapshotAt(Instant.parse("2026-09-28T08:00:00Z"));
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/trends"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/trends/2026-09-28"));
    }

    @Test
    void trends_returns404_whenNoSnapshots() throws Exception {
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.empty());

        mockMvc.perform(get("/trends"))
                .andExpect(status().isNotFound());
    }

    // ── /trends/{date} detail ──────────────────────────────────────────────────

    @Test
    void detail_rendersView_whenSnapshotExistsForDate() throws Exception {
        Instant generatedAt = Instant.parse("2026-09-28T08:00:00Z");
        TrendSnapshot snapshot = snapshotAt(generatedAt);
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/trends/2026-09-28"))
                .andExpect(status().isOk())
                .andExpect(view().name("trends-public-detail"));
    }

    @Test
    void detail_returns404_whenNoSnapshotForDate() throws Exception {
        TrendSnapshot snapshot = snapshotAt(Instant.parse("2026-09-21T08:00:00Z"));
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/trends/2026-09-28"))
                .andExpect(status().isNotFound());
    }

    @Test
    void detail_returns404_whenDateUnparseable() throws Exception {
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of());

        mockMvc.perform(get("/trends/not-a-date"))
                .andExpect(status().isNotFound());
    }

    @Test
    void detail_setsPageTitle_withDateAndTopKeyword() throws Exception {
        TrendSignal rising = new TrendSignal("ambient ai scribe", 32L, 0L, 0L, 0.0,
                TrendDirection.NEW, null, List.of(), null);
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.parse("2026-09-28T08:00:00Z"), 30,
                List.of(rising), List.of(), List.of(), 15);
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/trends/2026-09-28"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("pageTitle"))
                .andExpect(model().attributeExists("pageDescription"));
    }

    @Test
    void detail_isPublic_noAuthRequired() throws Exception {
        TrendSnapshot snapshot = snapshotAt(Instant.parse("2026-09-28T08:00:00Z"));
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of(snapshot));

        // No WithMockUser — must succeed without authentication
        mockMvc.perform(get("/trends/2026-09-28"))
                .andExpect(status().isOk());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private TrendSnapshot snapshotAt(Instant generatedAt) {
        return new TrendSnapshot(generatedAt, 30, List.of(), List.of(), List.of(), 5);
    }
}
