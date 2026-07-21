package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link AdminController}.
 *
 * <p>Admin panel is restricted to users with the ADMIN role.
 * All port calls are mocked — no real persistence occurs.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-31
 * @updated 2026-06-01
 */
@Import(SecurityConfig.class)
@WithMockUser(username = "admin@gmail.com", roles = "ADMIN")
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private GetAnalyticsUseCase analyticsUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @Test
    void adminPanel_rendersPageWithModelAttributes() throws Exception {
        AppUser admin = new AppUser("admin@gmail.com", "hash", "Admin", "ADMIN", true, null, null);
        AppUser user = new AppUser("demo@gmail.com", "hash", "Demo", "USER", true, null, null);
        when(appUserPort.findAll()).thenReturn(List.of(admin, user));

        IngestionAnalytics ingestion = new IngestionAnalytics(42, List.of(), List.of(), 10, 30, null);
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

        IngestionAnalytics ingestion = new IngestionAnalytics(0, List.of(), List.of(), 0, 0, null);
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
        AppUser admin1 = new AppUser("admin1@test.com", "hash", "Admin1", "ADMIN", true, null, null);
        AppUser admin2 = new AppUser("admin2@test.com", "hash", "Admin2", "ADMIN", true, null, null);
        AppUser user1 = new AppUser("user1@test.com", "hash", "User1", "USER", true, null, null);
        AppUser disabled = new AppUser("disabled@test.com", "hash", "Disabled", "USER", false, null, null);
        when(appUserPort.findAll()).thenReturn(List.of(admin1, admin2, user1, disabled));

        IngestionAnalytics ingestion = new IngestionAnalytics(0, List.of(), List.of(), 0, 0, null);
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

    // -------------------------------------------------------------------------
    // POST /admin/users/{email}/toggle-enabled
    // -------------------------------------------------------------------------

    @Test
    void toggleEnabled_disablesActiveUser() throws Exception {
        AppUser active = new AppUser("demo@gmail.com", "hash", "Demo", "USER", true, null, null);
        when(appUserPort.findByEmail("demo@gmail.com")).thenReturn(Optional.of(active));

        mockMvc.perform(post("/admin/users/demo@gmail.com/toggle-enabled").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("successMessage", "User demo@gmail.com has been disabled."));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserPort).save(captor.capture());
        assertThat(captor.getValue().enabled()).isFalse();
        assertThat(captor.getValue().role()).isEqualTo("USER");
    }

    @Test
    void toggleEnabled_enablesDisabledUser() throws Exception {
        AppUser disabled = new AppUser("demo@gmail.com", "hash", "Demo", "USER", false, null, null);
        when(appUserPort.findByEmail("demo@gmail.com")).thenReturn(Optional.of(disabled));

        mockMvc.perform(post("/admin/users/demo@gmail.com/toggle-enabled").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("successMessage", "User demo@gmail.com has been enabled."));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserPort).save(captor.capture());
        assertThat(captor.getValue().enabled()).isTrue();
    }

    @Test
    void toggleEnabled_cannotDisableSelf() throws Exception {
        mockMvc.perform(post("/admin/users/admin@gmail.com/toggle-enabled").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("errorMessage", "You cannot enable/disable your own account."));

        verify(appUserPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void toggleEnabled_userNotFound() throws Exception {
        when(appUserPort.findByEmail("unknown@gmail.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/admin/users/unknown@gmail.com/toggle-enabled").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("errorMessage", "User not found: unknown@gmail.com"));

        verify(appUserPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // POST /admin/users/{email}/change-role
    // -------------------------------------------------------------------------

    @Test
    void changeRole_switchesUserToAdmin() throws Exception {
        AppUser user = new AppUser("demo@gmail.com", "hash", "Demo", "USER", true, null, null);
        when(appUserPort.findByEmail("demo@gmail.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/admin/users/demo@gmail.com/change-role")
                        .param("role", "ADMIN")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("successMessage", "User demo@gmail.com role changed to ADMIN."));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserPort).save(captor.capture());
        assertThat(captor.getValue().role()).isEqualTo("ADMIN");
        assertThat(captor.getValue().enabled()).isTrue();
    }

    @Test
    void changeRole_switchesAdminToUser() throws Exception {
        AppUser admin = new AppUser("other-admin@gmail.com", "hash", "OtherAdmin", "ADMIN", true, null, null);
        when(appUserPort.findByEmail("other-admin@gmail.com")).thenReturn(Optional.of(admin));

        mockMvc.perform(post("/admin/users/other-admin@gmail.com/change-role")
                        .param("role", "USER")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserPort).save(captor.capture());
        assertThat(captor.getValue().role()).isEqualTo("USER");
    }

    @Test
    void changeRole_cannotDemoteSelf() throws Exception {
        mockMvc.perform(post("/admin/users/admin@gmail.com/change-role")
                        .param("role", "USER")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("errorMessage", "You cannot change your own role."));

        verify(appUserPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(username = "user@gmail.com", roles = "USER")
    void changeRole_asUser_returns403() throws Exception {
        mockMvc.perform(post("/admin/users/demo@gmail.com/change-role")
                        .param("role", "ADMIN")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@gmail.com", roles = "USER")
    void toggleEnabled_asUser_returns403() throws Exception {
        mockMvc.perform(post("/admin/users/demo@gmail.com/toggle-enabled").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
