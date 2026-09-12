package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link TrendHistoryController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@WebMvcTest(TrendHistoryController.class)
class TrendHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DetectTrendsUseCase detectTrendsUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private TierResolver tierResolver;

    @BeforeEach
    void setUp() {
        when(subscriberPort.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @WithMockUser
    void historyPage_rendersWithSnapshots() throws Exception {
        TrendSignal rising = new TrendSignal("genomics", 15, 3, 2,
                5.0, TrendDirection.RISING, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 42);
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/dashboard/trends/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("trend-history"))
                .andExpect(model().attribute("hasHistory", true))
                .andExpect(model().attribute("snapshotCount", 1));
    }

    @Test
    @WithMockUser
    void historyPage_rendersWhenNoSnapshots() throws Exception {
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/trends/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("trend-history"))
                .andExpect(model().attribute("hasHistory", false));
    }

    @Test
    @WithMockUser
    void historyPage_freeUserGetsLimitedSnapshots() throws Exception {
        List<TrendSnapshot> manySnapshots = buildSnapshots(6);
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(manySnapshots);

        mockMvc.perform(get("/dashboard/trends/history"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", false))
                .andExpect(model().attribute("snapshotCount", 6));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void historyPage_adminGetsFullAccess() throws Exception {
        when(tierResolver.isAdmin(any())).thenReturn(true);
        List<TrendSnapshot> manySnapshots = buildSnapshots(6);
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(manySnapshots);

        mockMvc.perform(get("/dashboard/trends/history"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", true));
    }

    @Test
    void historyPage_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard/trends/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void historyPage_includesChartData() throws Exception {
        TrendSignal rising = new TrendSignal("telehealth", 12, 4, 1,
                3.0, TrendDirection.RISING, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 30);
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/dashboard/trends/history"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("historyDates", "keywordNames", "keywordCounts"));
    }

    @Test
    @WithMockUser
    void detailPage_rendersForValidEpochMillis() throws Exception {
        Instant now = Instant.now();
        TrendSignal rising = new TrendSignal("radiology ai", 10, 3, 2,
                3.33, TrendDirection.RISING, now);
        TrendSnapshot snapshot = new TrendSnapshot(
                now, 30, List.of(rising), List.of(), List.of(), 42);
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/dashboard/trends/history/" + now.toEpochMilli()))
                .andExpect(status().isOk())
                .andExpect(view().name("trend-history-detail"))
                .andExpect(model().attribute("totalKeywords", 42))
                .andExpect(model().attributeExists("risingTopics", "chartLabels", "chartData"));
    }

    @Test
    @WithMockUser
    void detailPage_returns404ForInvalidEpochMillis() throws Exception {
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/trends/history/9999999999999"))
                .andExpect(status().isNotFound());
    }

    private List<TrendSnapshot> buildSnapshots(int count) {
        List<TrendSnapshot> snapshots = new ArrayList<>();
        Instant base = Instant.now();
        for (int i = 0; i < count; i++) {
            TrendSignal signal = new TrendSignal("keyword" + i, 10 - i, 2, 1,
                    3.0, TrendDirection.RISING, base);
            snapshots.add(new TrendSnapshot(
                    base.minus(i * 7L, ChronoUnit.DAYS), 30,
                    List.of(signal), List.of(), List.of(), 50));
        }
        return snapshots;
    }
}
