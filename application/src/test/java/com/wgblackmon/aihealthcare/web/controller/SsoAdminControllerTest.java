package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSsoProvidersUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link SsoAdminController}.
 *
 * <p>Verifies SSO provider CRUD operations in the admin UI.
 * All endpoints require the ADMIN role.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
@Import(SecurityConfig.class)
@WithMockUser(username = "admin@test.com", roles = "ADMIN")
@WebMvcTest(SsoAdminController.class)
class SsoAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private ManageSsoProvidersUseCase ssoUseCase;

    private static SsoIdentityProvider testProvider() {
        Instant now = Instant.parse("2026-09-15T12:00:00Z");
        return new SsoIdentityProvider(
                "acme-health", "Acme Health SSO",
                "https://idp.acme.com/saml", "https://idp.acme.com/saml/sso",
                "MIICdummycert", null,
                "email", "displayName",
                SubscriptionTier.ENTERPRISE, true, now, now);
    }

    @Test
    void listProviders_returnsProvidersList() throws Exception {
        when(ssoUseCase.getAll()).thenReturn(List.of(testProvider()));

        mockMvc.perform(get("/admin/sso"))
                .andExpect(status().isOk())
                .andExpect(view().name("sso-providers"))
                .andExpect(model().attributeExists("providers", "updatedDates"));
    }

    @Test
    void listProviders_emptyList() throws Exception {
        when(ssoUseCase.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/sso"))
                .andExpect(status().isOk())
                .andExpect(view().name("sso-providers"));
    }

    @Test
    void newProviderForm_rendersForm() throws Exception {
        mockMvc.perform(get("/admin/sso/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("sso-provider-form"))
                .andExpect(model().attribute("editMode", false));
    }

    @Test
    void editProviderForm_existingProvider() throws Exception {
        when(ssoUseCase.getById("acme-health")).thenReturn(Optional.of(testProvider()));

        mockMvc.perform(get("/admin/sso/acme-health/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("sso-provider-form"))
                .andExpect(model().attribute("editMode", true))
                .andExpect(model().attributeExists("provider"));
    }

    @Test
    void editProviderForm_notFound_redirectsToList() throws Exception {
        when(ssoUseCase.getById("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/sso/unknown/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/sso"));
    }

    @Test
    void createProvider_redirectsWithSuccess() throws Exception {
        when(ssoUseCase.create(any())).thenReturn(testProvider());

        mockMvc.perform(post("/admin/sso").with(csrf())
                        .param("registrationId", "acme-health")
                        .param("label", "Acme Health SSO")
                        .param("entityId", "https://idp.acme.com/saml")
                        .param("ssoUrl", "https://idp.acme.com/saml/sso")
                        .param("certificate", "MIICdummycert")
                        .param("emailAttribute", "email")
                        .param("displayNameAttribute", "displayName")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/sso"));

        verify(ssoUseCase).create(any(SsoIdentityProvider.class));
    }

    @Test
    void deleteProvider_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/admin/sso/acme-health/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/sso"));

        verify(ssoUseCase).delete("acme-health");
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void listProviders_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/admin/sso"))
                .andExpect(status().isForbidden());
    }
}
