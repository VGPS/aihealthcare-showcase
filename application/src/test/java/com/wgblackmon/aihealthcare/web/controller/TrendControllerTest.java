package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.mockito.Mockito.when;

/**
 * MockMvc tests for {@link TrendController}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-07-22
 * @updated 2026-07-24
 */
@WebMvcTest(TrendController.class)
class TrendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DetectTrendsUseCase detectTrendsUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @Test
    @WithMockUser
    void trendsPage_rendersWithSnapshot() throws Exception {
        TrendSignal rising = new TrendSignal("radiology ai", 10, 3, 2,
                3.33, TrendDirection.RISING, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 42);
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/dashboard/trends"))
                .andExpect(status().isOk())
                .andExpect(view().name("trends"))
                .andExpect(model().attribute("hasSnapshot", true))
                .andExpect(model().attribute("totalKeywords", 42));
    }

    @Test
    @WithMockUser
    void trendsPage_rendersWhenNoSnapshot() throws Exception {
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.empty());

        mockMvc.perform(get("/dashboard/trends"))
                .andExpect(status().isOk())
                .andExpect(view().name("trends"))
                .andExpect(model().attribute("hasSnapshot", false));
    }

    @Test
    @WithMockUser
    void trendsPage_limitsRisingForFreeUser() throws Exception {
        List<TrendSignal> manyRising = List.of(
                new TrendSignal("keyword1", 10, 2, 1, 5.0, TrendDirection.RISING, Instant.now()),
                new TrendSignal("keyword2", 9, 2, 1, 4.5, TrendDirection.RISING, Instant.now()),
                new TrendSignal("keyword3", 8, 2, 1, 4.0, TrendDirection.RISING, Instant.now()),
                new TrendSignal("keyword4", 7, 2, 1, 3.5, TrendDirection.RISING, Instant.now()),
                new TrendSignal("keyword5", 6, 2, 1, 3.0, TrendDirection.RISING, Instant.now()),
                new TrendSignal("keyword6", 5, 2, 1, 2.5, TrendDirection.RISING, Instant.now()),
                new TrendSignal("keyword7", 4, 2, 1, 2.0, TrendDirection.RISING, Instant.now())
        );
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, manyRising, List.of(), List.of(), 100);
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/dashboard/trends"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void trendsPage_adminGetsFullAccess() throws Exception {
        TrendSignal rising = new TrendSignal("genomics", 15, 3, 2,
                5.0, TrendDirection.RISING, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 50);
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/dashboard/trends"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", true));
    }

    @Test
    void trendsPage_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard/trends"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void trendsPage_includesChartData() throws Exception {
        TrendSignal rising = new TrendSignal("genomics", 15, 3, 2,
                5.0, TrendDirection.RISING, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 25);
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/dashboard/trends"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("chartLabels", "chartData"));
    }

    @Test
    @WithMockUser
    void trendsPage_includesScoringRubric() throws Exception {
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(), List.of(), List.of(), 10);
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/dashboard/trends"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("scoringRubric"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void trendsPage_risingSignalWithScoredArticles() throws Exception {
        ScoredArticle scored = new ScoredArticle("a1", "FDA clears AI tool", 8,
                "Major regulatory milestone", "radiology ai");
        TrendSignal rising = new TrendSignal("radiology ai", 10, 3, 2,
                3.33, TrendDirection.RISING, Instant.now(), List.of(scored));
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(rising), List.of(), List.of(), 42);
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/dashboard/trends"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasSnapshot", true))
                .andExpect(model().attribute("fullAccess", true));
    }
}
