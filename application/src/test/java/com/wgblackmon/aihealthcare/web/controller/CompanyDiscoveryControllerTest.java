package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyDiscoveryResult;
import com.wgblackmon.aihealthcare.domain.model.CompanyTags;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.DiscoverCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link CompanyDiscoveryController}.
 *
 * <p>Covers the {@code POST /api/v1/companies/discover} endpoint including
 * SUBSCRIBER tier gating via the {@code X-Subscriber-Email} header.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-06-07
 * @updated 2026-06-07
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(CompanyDiscoveryController.class)
class CompanyDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ApiKeyPort apiKeyPort;

    @MockBean
    private DiscoverCompaniesUseCase discoverCompaniesUseCase;

    @MockBean
    private SubscriberPort subscriberPort;

    private void stubSubscriberTier(String email) {
        Subscriber subscriber = new Subscriber(email, "Subscriber User", true, Instant.now(), SubscriptionTier.SUBSCRIBER, null, null, null);
        when(subscriberPort.findByEmail(email)).thenReturn(Optional.of(subscriber));
    }

    // -------------------------------------------------------------------------
    // Tier gating
    // -------------------------------------------------------------------------

    @Test
    void discover_noHeader_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/companies/discover"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Company discovery is a Subscriber-only feature"))
                .andExpect(jsonPath("$.tier").value("FREE"));
    }

    @Test
    void discover_freeSubscriber_returns403() throws Exception {
        Subscriber free = new Subscriber("free@example.com", "Free User", true, Instant.now(), SubscriptionTier.FREE, null, null, null);
        when(subscriberPort.findByEmail("free@example.com")).thenReturn(Optional.of(free));

        mockMvc.perform(post("/api/v1/companies/discover")
                        .header("X-Subscriber-Email", "free@example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Company discovery is a Subscriber-only feature"))
                .andExpect(jsonPath("$.tier").value("FREE"));
    }

    @Test
    void discover_unknownEmail_returns403() throws Exception {
        when(subscriberPort.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/companies/discover")
                        .header("X-Subscriber-Email", "unknown@example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.tier").value("FREE"));
    }

    // -------------------------------------------------------------------------
    // Successful discovery (SUBSCRIBER tier)
    // -------------------------------------------------------------------------

    @Test
    void discover_returns200WithCompanies() throws Exception {
        stubSubscriberTier("subscriber@example.com");

        Company company = new Company("ScribeBot", "YC Health Tech",
                "https://yc.com/scribebot", "https://scribebot.com",
                "AI medical scribe for clinical documentation.",
                new CompanyTags(true, false, false, false, false), true, true);

        CompanyDiscoveryResult result = new CompanyDiscoveryResult(
                List.of(company), "# Markdown", 10, 8, 1);

        when(discoverCompaniesUseCase.discover()).thenReturn(result);

        mockMvc.perform(post("/api/v1/companies/discover")
                        .header("X-Subscriber-Email", "subscriber@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScraped").value(10))
                .andExpect(jsonPath("$.afterDedup").value(8))
                .andExpect(jsonPath("$.aiHealthFiltered").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("ScribeBot"))
                .andExpect(jsonPath("$.companies[0].scribe").value(true))
                .andExpect(jsonPath("$.companies[0].isAI").value(true))
                .andExpect(jsonPath("$.companies[0].isHealth").value(true))
                .andExpect(jsonPath("$.markdown").value("# Markdown"));
    }

    @Test
    void discover_emptyResult_returns200() throws Exception {
        stubSubscriberTier("subscriber@example.com");

        CompanyDiscoveryResult result = new CompanyDiscoveryResult(
                List.of(), "No companies found.", 0, 0, 0);

        when(discoverCompaniesUseCase.discover()).thenReturn(result);

        mockMvc.perform(post("/api/v1/companies/discover")
                        .header("X-Subscriber-Email", "subscriber@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScraped").value(0))
                .andExpect(jsonPath("$.companies").isEmpty());
    }

    @Test
    void discover_multipleCompanies_returnsAll() throws Exception {
        stubSubscriberTier("subscriber@example.com");

        Company c1 = new Company("ScribeBot", "YC Health Tech",
                "https://yc.com/sb", null, "AI scribe.",
                new CompanyTags(true, false, false, false, false), true, true);
        Company c2 = new Company("ImageAI", "TopStartups Healthcare",
                "https://ts.io/img", "https://imageai.com", "Imaging AI.",
                new CompanyTags(false, false, true, false, false), true, true);

        CompanyDiscoveryResult result = new CompanyDiscoveryResult(
                List.of(c1, c2), "# Markdown", 20, 15, 2);

        when(discoverCompaniesUseCase.discover()).thenReturn(result);

        mockMvc.perform(post("/api/v1/companies/discover")
                        .header("X-Subscriber-Email", "subscriber@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(2))
                .andExpect(jsonPath("$.companies[0].name").value("ScribeBot"))
                .andExpect(jsonPath("$.companies[1].name").value("ImageAI"))
                .andExpect(jsonPath("$.companies[1].companySite").value("https://imageai.com"));
    }

    @Test
    void discover_companyTagsFlattened() throws Exception {
        stubSubscriberTier("subscriber@example.com");

        Company company = new Company("OmniHealth", "anchor",
                "https://omni.com", "https://omni.com",
                "Full-stack healthcare AI.",
                new CompanyTags(true, true, true, true, true), true, true);

        CompanyDiscoveryResult result = new CompanyDiscoveryResult(
                List.of(company), "# Markdown", 1, 1, 1);

        when(discoverCompaniesUseCase.discover()).thenReturn(result);

        mockMvc.perform(post("/api/v1/companies/discover")
                        .header("X-Subscriber-Email", "subscriber@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].scribe").value(true))
                .andExpect(jsonPath("$.companies[0].agent").value(true))
                .andExpect(jsonPath("$.companies[0].imaging").value(true))
                .andExpect(jsonPath("$.companies[0].rcm").value(true))
                .andExpect(jsonPath("$.companies[0].infra").value(true));
    }
}
