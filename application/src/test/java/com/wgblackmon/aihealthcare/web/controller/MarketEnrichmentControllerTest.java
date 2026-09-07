package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.DealTerms;
import com.wgblackmon.aihealthcare.domain.marketanalysis.DisclosedPortion;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PrivateFundingRound;
import com.wgblackmon.aihealthcare.domain.marketanalysis.RegulatoryTracker;
import com.wgblackmon.aihealthcare.domain.marketanalysis.Jurisdiction;
import com.wgblackmon.aihealthcare.domain.marketanalysis.RulemakingStage;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.DealTermsPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.PrivateFundingPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.RegulatoryTrackerRepository;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
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
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link MarketEnrichmentController}.
 *
 * <p>Verifies routing, model population, tier gating, and empty-state
 * handling for the Market Enrichment tabbed page.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
@Import(SecurityConfig.class)
@WebMvcTest(MarketEnrichmentController.class)
class MarketEnrichmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegulatoryTrackerRepository regulatoryTrackerRepository;

    @MockitoBean
    private PrivateFundingPort privateFundingPort;

    @MockitoBean
    private DealTermsPort dealTermsPort;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    @DisplayName("Unauthenticated request redirects to login")
    void unauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/dashboard/market/enrichment"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Admin sees enrichment page with all data")
    void adminSeesFullEnrichmentPage() throws Exception {
        RegulatoryTracker tracker = new RegulatoryTracker(
                Jurisdiction.US_FDA, RulemakingStage.COMMENT_PERIOD,
                "FDA-2026-N-0001", "FDA AI Device Rule", LocalDate.now().plusDays(15), Instant.now(), null);
        when(regulatoryTrackerRepository.findAll()).thenReturn(List.of(tracker));
        when(regulatoryTrackerRepository.findApproachingDeadlines(any())).thenReturn(List.of(tracker));

        PrivateFundingRound round = new PrivateFundingRound(
                "HealthTech Inc", "Series B", 75_000_000L, List.of("Sequoia"), Instant.now(), null);
        when(privateFundingPort.findRecentRounds(any(), any())).thenReturn(List.of(round));

        DealTerms terms = new DealTerms(500_000_000L, 200_000_000L, null, null, DisclosedPortion.PARTIAL, null);
        when(dealTermsPort.findAllWithHeadlines()).thenReturn(Map.of("Acme acquires BetaCo", terms));

        mockMvc.perform(get("/dashboard/market/enrichment"))
                .andExpect(status().isOk())
                .andExpect(view().name("market-enrichment"))
                .andExpect(model().attribute("totalTrackers", 1))
                .andExpect(model().attribute("totalFunding", 1))
                .andExpect(model().attribute("totalDealTerms", 1))
                .andExpect(model().attribute("fullAccess", true))
                .andExpect(model().attribute("approachingCount", 1));
    }

    @Test
    @WithMockUser(username = "free@test.com")
    @DisplayName("FREE user sees limited data and tier gate flag")
    void freeUserSeesLimitedData() throws Exception {
        when(subscriberPort.findByEmail("free@test.com"))
                .thenReturn(Optional.of(new Subscriber("free@test.com", "Free User",
                        true, null, SubscriptionTier.FREE, null, null, null)));
        when(regulatoryTrackerRepository.findAll()).thenReturn(List.of());
        when(regulatoryTrackerRepository.findApproachingDeadlines(any())).thenReturn(List.of());
        when(privateFundingPort.findRecentRounds(any(), any())).thenReturn(List.of());
        when(dealTermsPort.findAllWithHeadlines()).thenReturn(Map.of());

        mockMvc.perform(get("/dashboard/market/enrichment"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", false))
                .andExpect(model().attribute("freeLimit", 3));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Empty state renders without errors")
    void emptyStateRendersCleanly() throws Exception {
        when(regulatoryTrackerRepository.findAll()).thenReturn(List.of());
        when(regulatoryTrackerRepository.findApproachingDeadlines(any())).thenReturn(List.of());
        when(privateFundingPort.findRecentRounds(any(), any())).thenReturn(List.of());
        when(dealTermsPort.findAllWithHeadlines()).thenReturn(Map.of());

        mockMvc.perform(get("/dashboard/market/enrichment"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalTrackers", 0))
                .andExpect(model().attribute("totalFunding", 0))
                .andExpect(model().attribute("totalDealTerms", 0));
    }

    @Test
    @WithMockUser(username = "sub@test.com")
    @DisplayName("SUBSCRIBER user gets full access")
    void subscriberGetsFullAccess() throws Exception {
        when(subscriberPort.findByEmail("sub@test.com"))
                .thenReturn(Optional.of(new Subscriber("sub@test.com", "Subscriber",
                        true, null, SubscriptionTier.SUBSCRIBER, null, null, null)));
        when(regulatoryTrackerRepository.findAll()).thenReturn(List.of());
        when(regulatoryTrackerRepository.findApproachingDeadlines(any())).thenReturn(List.of());
        when(privateFundingPort.findRecentRounds(any(), any())).thenReturn(List.of());
        when(dealTermsPort.findAllWithHeadlines()).thenReturn(Map.of());

        mockMvc.perform(get("/dashboard/market/enrichment"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", true));
    }

    @Test
    @DisplayName("formatUsd formats amounts correctly")
    void formatUsdFormatsAmountsCorrectly() {
        MarketEnrichmentController controller = new MarketEnrichmentController(null, null, null,
                new NoOpSubscriberPort());

        org.assertj.core.api.Assertions.assertThat(controller.formatUsd(null)).isEqualTo("—");
        org.assertj.core.api.Assertions.assertThat(controller.formatUsd(2_500_000_000L)).isEqualTo("$2.5B");
        org.assertj.core.api.Assertions.assertThat(controller.formatUsd(150_000_000L)).isEqualTo("$150.0M");
        org.assertj.core.api.Assertions.assertThat(controller.formatUsd(75_000L)).isEqualTo("$75K");
        org.assertj.core.api.Assertions.assertThat(controller.formatUsd(500L)).isEqualTo("$500");
    }

    private static class NoOpSubscriberPort implements SubscriberPort {
        public Optional<Subscriber> findByEmail(String email) { return Optional.empty(); }
        public void save(Subscriber subscriber) {}
        public void deleteByEmail(String email) {}
        public List<Subscriber> findAll() { return List.of(); }
        public List<Subscriber> findAllActiveByTier(SubscriptionTier tier) { return List.of(); }
        public Optional<Subscriber> findByUnsubscribeToken(String token) { return Optional.empty(); }
    }
}
