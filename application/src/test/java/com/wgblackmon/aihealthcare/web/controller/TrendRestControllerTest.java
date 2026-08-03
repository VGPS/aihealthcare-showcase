package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link TrendRestController}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-07-22
 * @updated 2026-08-03
 */
@WebMvcTest(TrendRestController.class)
class TrendRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DetectTrendsUseCase detectTrendsUseCase;

    @Test
    @WithMockUser
    void getLatest_returnsSnapshot() throws Exception {
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(), List.of(), List.of(), 42);
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/api/v1/trends/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowDays").value(30))
                .andExpect(jsonPath("$.totalKeywords").value(42));
    }

    @Test
    @WithMockUser
    void getLatest_returnsNoContentWhenEmpty() throws Exception {
        when(detectTrendsUseCase.getLatestSnapshot()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/trends/latest"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void triggerDetection_returnsNewSnapshot() throws Exception {
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(), List.of(), List.of(), 15);
        when(detectTrendsUseCase.detectTrends()).thenReturn(snapshot);

        mockMvc.perform(post("/api/v1/trends/detect")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalKeywords").value(15));
    }

    @Test
    void getLatest_requiresAuth_inWebMvcTest() throws Exception {
        // In @WebMvcTest, SecurityConfig permitAll for /api/** isn't loaded
        mockMvc.perform(get("/api/v1/trends/latest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getHistory_returnsSnapshotList() throws Exception {
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(), List.of(), List.of(), 25);
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/api/v1/trends/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].totalKeywords").value(25));
    }

    @Test
    @WithMockUser
    void getHistory_returnsEmptyArray() throws Exception {
        when(detectTrendsUseCase.getAllSnapshots()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/trends/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
