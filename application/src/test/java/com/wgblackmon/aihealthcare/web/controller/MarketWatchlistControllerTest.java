package com.wgblackmon.aihealthcare.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.TickerWatchlistRepository;
import com.wgblackmon.aihealthcare.web.dto.WatchlistUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link MarketWatchlistController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@WebMvcTest(MarketWatchlistController.class)
class MarketWatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TickerWatchlistRepository watchlistRepository;

    // ─── GET /api/market-digest/watchlist ────────────────────────────────────

    @Test
    @WithMockUser(username = "user@example.com")
    void getWatchlist_returnsCurrentTickers() throws Exception {
        when(watchlistRepository.findWatchedTickers("user@example.com"))
                .thenReturn(List.of("AAPL", "NVDA"));

        mockMvc.perform(get("/api/market-digest/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriberId").value("user@example.com"))
                .andExpect(jsonPath("$.tickers.length()").value(2))
                .andExpect(jsonPath("$.tickers[0]").value("AAPL"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void getWatchlist_whenEmpty_returnsEmptyList() throws Exception {
        when(watchlistRepository.findWatchedTickers("user@example.com"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/market-digest/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickers.length()").value(0));
    }

    @Test
    void getWatchlist_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/market-digest/watchlist"))
                .andExpect(status().isUnauthorized());
    }

    // ─── PUT /api/market-digest/watchlist ────────────────────────────────────

    @Test
    @WithMockUser(username = "user@example.com")
    void replaceWatchlist_callsReplaceAndReturnsUpdated() throws Exception {
        List<String> newTickers = List.of("MSFT", "GOOG");
        when(watchlistRepository.findWatchedTickers("user@example.com"))
                .thenReturn(newTickers);

        WatchlistUpdateRequest request = new WatchlistUpdateRequest(newTickers);

        mockMvc.perform(put("/api/market-digest/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickers.length()").value(2));

        verify(watchlistRepository).replaceWatchlist(eq("user@example.com"), any());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void replaceWatchlist_withEmptyList_clearsWatchlist() throws Exception {
        when(watchlistRepository.findWatchedTickers("user@example.com"))
                .thenReturn(List.of());

        WatchlistUpdateRequest request = new WatchlistUpdateRequest(List.of());

        mockMvc.perform(put("/api/market-digest/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickers.length()").value(0));
    }

    // ─── POST /api/market-digest/subscribe ───────────────────────────────────

    @Test
    @WithMockUser(username = "user@example.com")
    void subscribe_returnsCurrentWatchlist() throws Exception {
        when(watchlistRepository.findWatchedTickers("user@example.com"))
                .thenReturn(List.of("AMZN"));

        mockMvc.perform(post("/api/market-digest/subscribe").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriberId").value("user@example.com"))
                .andExpect(jsonPath("$.tickers[0]").value("AMZN"));
    }

    @Test
    @WithMockUser(username = "new@example.com")
    void subscribe_firstTime_returnsEmptyWatchlist() throws Exception {
        when(watchlistRepository.findWatchedTickers("new@example.com"))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/market-digest/subscribe").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickers.length()").value(0));
    }
}
