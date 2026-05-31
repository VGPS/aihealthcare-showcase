package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link AdminController}.
 *
 * <p>Admin panel is restricted to users with the ADMIN role.
 * All port calls are mocked — no real persistence occurs.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-31
 * @updated 2026-05-31
 */
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private GetAnalyticsUseCase analyticsUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @Test
    void adminPanel_rendersPageWithModelAttributes() throws Exception {
        AppUser admin = new AppUser("admin@gmail.com", "hash", "Admin", "ADMIN", true);
        AppUser user = new AppUser("demo@gmail.com", "hash", "Demo", "USER", true);
        when(appUserPort.findAll()).thenReturn(List.of(admin, user));

        IngestionAnalytics ingestion = new IngestionAnalytics(42, List.of(), List.of(), 10, 30);
        when(analyticsUseCase.getIngestionAnalytics()).thenReturn(ingestion);

        RunAnalytics runs = new RunAnalytics(5, 2, 3, 0, Instant.now());
        when(analyticsUseCase.getRunAnalytics()).thenReturn(runs);

        Subscriber sub = new Subscriber("sub@example.com", "Sub", true,
                Instant.now(), SubscriptionTier.FREE);
        when(subscriberPort.findAll()).thenReturn(List.of(sub));

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(model().attributeExists("users"))
                .andExpect(model().attributeExists("adminCount"))
                .andExpect(model().attributeExists("userCount"))
                .andExpect(model().attributeExists("ingestion"))
                .andExpect(model().attributeExists("runs"))
                .andExpect(model().attributeExists("subscriberCount"));
    }

    @Test
    void adminPanel_noUsers_rendersEmptyList() throws Exception {
        when(appUserPort.findAll()).thenReturn(List.of());

        IngestionAnalytics ingestion = new IngestionAnalytics(0, List.of(), List.of(), 0, 0);
        when(analyticsUseCase.getIngestionAnalytics()).thenReturn(ingestion);

        RunAnalytics runs = new RunAnalytics(0, 0, 0, 0, null);
        when(analyticsUseCase.getRunAnalytics()).thenReturn(runs);

        when(subscriberPort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(model().attribute("adminCount", 0L))
                .andExpect(model().attribute("userCount", 0L));
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminPanel_asUser_returns403() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPanel_countsRolesCorrectly() throws Exception {
        AppUser admin1 = new AppUser("admin1@test.com", "hash", "Admin1", "ADMIN", true);
        AppUser admin2 = new AppUser("admin2@test.com", "hash", "Admin2", "ADMIN", true);
        AppUser user1 = new AppUser("user1@test.com", "hash", "User1", "USER", true);
        AppUser disabled = new AppUser("disabled@test.com", "hash", "Disabled", "USER", false);
        when(appUserPort.findAll()).thenReturn(List.of(admin1, admin2, user1, disabled));

        IngestionAnalytics ingestion = new IngestionAnalytics(0, List.of(), List.of(), 0, 0);
        when(analyticsUseCase.getIngestionAnalytics()).thenReturn(ingestion);

        RunAnalytics runs = new RunAnalytics(0, 0, 0, 0, null);
        when(analyticsUseCase.getRunAnalytics()).thenReturn(runs);

        when(subscriberPort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("adminCount", 2L))
                .andExpect(model().attribute("userCount", 2L))
                .andExpect(model().attribute("disabledCount", 1L));
    }
}
