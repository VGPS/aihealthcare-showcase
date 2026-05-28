package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import com.wgblackmon.aihealthcare.infrastructure.config.TierLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link PricingController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(PricingController.class)
class PricingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TierLimitProperties tierLimitProperties;

    @MockitoBean
    private StripeProperties stripeProperties;

    private void stubProperties() {
        TierLimitProperties.TierConfig free = new TierLimitProperties.TierConfig();
        free.setArchiveDays(30);
        free.setMonthlyQueryLimit(15);

        TierLimitProperties.TierConfig member = new TierLimitProperties.TierConfig();
        member.setArchiveDays(0);
        member.setMonthlyQueryLimit(200);

        when(tierLimitProperties.getFree()).thenReturn(free);
        when(tierLimitProperties.getMember()).thenReturn(member);
        when(stripeProperties.isEnabled()).thenReturn(true);
        when(stripeProperties.getPublishableKey()).thenReturn("pk_test_123");
        when(stripeProperties.getMemberPriceId()).thenReturn("price_member_123");
    }

    @Test
    void pricing_returns200() throws Exception {
        stubProperties();

        mockMvc.perform(get("/pricing"))
                .andExpect(status().isOk())
                .andExpect(view().name("pricing"));
    }

    @Test
    void pricing_modelContainsTierDetails() throws Exception {
        stubProperties();

        mockMvc.perform(get("/pricing"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("freeArchiveDays", 30))
                .andExpect(model().attribute("freeQueryLimit", 15))
                .andExpect(model().attribute("memberArchiveDays", 0))
                .andExpect(model().attribute("memberQueryLimit", 200));
    }

    @Test
    void pricing_modelContainsStripeConfig() throws Exception {
        stubProperties();

        mockMvc.perform(get("/pricing"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("stripeEnabled", true))
                .andExpect(model().attribute("stripePublishableKey", "pk_test_123"))
                .andExpect(model().attribute("memberPriceId", "price_member_123"));
    }
}
