package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link WatchlistController}.
 *
 * <p>Verifies GET rendering, POST add/delete, tier gating (FREE redirects
 * to pricing), and unauthenticated access returns 401.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-08-04
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(WatchlistController.class)
class WatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WatchlistPort watchlistPort;

    @MockitoBean
    private WatchlistMatchPort watchlistMatchPort;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private NewsArticleRepository articleRepository;

    private void stubSubscriberUser() {
        AppUser user = new AppUser("user", "hashed", "Test User", "USER", true,
                SubscriptionTier.SUBSCRIBER, null);
        when(appUserPort.findByEmail("user")).thenReturn(Optional.of(user));
    }

    private void stubDemoUser() {
        AppUser user = new AppUser("user", "hashed", "Demo User", "USER", true,
                SubscriptionTier.DEMO, null);
        when(appUserPort.findByEmail("user")).thenReturn(Optional.of(user));
    }

    private void stubFreeUser() {
        AppUser user = new AppUser("user", "hashed", "Free User", "USER", true,
                SubscriptionTier.FREE, null);
        when(appUserPort.findByEmail("user")).thenReturn(Optional.of(user));
    }

    private void stubAdminUser() {
        AppUser user = new AppUser("user", "hashed", "Admin User", "ADMIN", true,
                SubscriptionTier.FREE, null);
        when(appUserPort.findByEmail("user")).thenReturn(Optional.of(user));
    }

    @Test
    void getWatchlist_subscriberUser_rendersPage() throws Exception {
        stubSubscriberUser();
        Instant now = Instant.now();
        WatchlistItem item = new WatchlistItem("w1", "user",
                WatchlistItemType.KEYWORD, "FDA", "FDA", now);
        when(watchlistPort.findByUser("user")).thenReturn(List.of(item));
        when(watchlistMatchPort.findByUser("user", 50)).thenReturn(List.of());

        mockMvc.perform(get("/watchlist"))
                .andExpect(status().isOk())
                .andExpect(view().name("watchlist"))
                .andExpect(model().attribute("itemCount", 1))
                .andExpect(model().attribute("matchCount", 0))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FDA")));
    }

    @Test
    void getWatchlist_demoUser_rendersPage() throws Exception {
        stubDemoUser();
        when(watchlistPort.findByUser("user")).thenReturn(List.of());
        when(watchlistMatchPort.findByUser("user", 50)).thenReturn(List.of());

        mockMvc.perform(get("/watchlist"))
                .andExpect(status().isOk())
                .andExpect(view().name("watchlist"))
                .andExpect(model().attribute("itemCount", 0));
    }

    @Test
    void getWatchlist_freeUser_redirectsToPricing() throws Exception {
        stubFreeUser();

        mockMvc.perform(get("/watchlist"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pricing"));
    }

    @Test
    void getWatchlist_adminUser_rendersPage() throws Exception {
        stubAdminUser();
        when(watchlistPort.findByUser("user")).thenReturn(List.of());
        when(watchlistMatchPort.findByUser("user", 50)).thenReturn(List.of());

        mockMvc.perform(get("/watchlist"))
                .andExpect(status().isOk())
                .andExpect(view().name("watchlist"));
    }

    @Test
    void getWatchlist_withMatches_rendersMatchTable() throws Exception {
        stubSubscriberUser();
        Instant now = Instant.now();
        WatchlistItem item = new WatchlistItem("w1", "user",
                WatchlistItemType.KEYWORD, "FDA", "FDA", now);
        WatchlistMatch match = new WatchlistMatch("m1", "w1", "a1", now, "FDA cleared device...");
        when(watchlistPort.findByUser("user")).thenReturn(List.of(item));
        when(watchlistMatchPort.findByUser("user", 50)).thenReturn(List.of(match));

        mockMvc.perform(get("/watchlist"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("matchCount", 1))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FDA cleared device...")));
    }

    @Test
    void postWatchlist_subscriberUser_addsItemAndRedirects() throws Exception {
        stubSubscriberUser();

        mockMvc.perform(post("/watchlist")
                        .param("itemType", "KEYWORD")
                        .param("value", "FDA clearance")
                        .param("label", "FDA Clearances")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/watchlist"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(watchlistPort).save(any(WatchlistItem.class));
    }

    @Test
    void postWatchlist_freeUser_redirectsToPricing() throws Exception {
        stubFreeUser();

        mockMvc.perform(post("/watchlist")
                        .param("itemType", "KEYWORD")
                        .param("value", "FDA")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pricing"));

        verify(watchlistPort, never()).save(any());
    }

    @Test
    void deleteItem_subscriberUser_deletesAndRedirects() throws Exception {
        stubSubscriberUser();

        mockMvc.perform(post("/watchlist/w1/delete")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/watchlist"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(watchlistPort).delete("w1", "user");
    }

    @Test
    @WithMockUser(username = "unauthenticated_test")
    void getWatchlist_unknownUser_redirectsToPricing() throws Exception {
        when(appUserPort.findByEmail("unauthenticated_test")).thenReturn(Optional.empty());

        mockMvc.perform(get("/watchlist"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pricing"));
    }
}
