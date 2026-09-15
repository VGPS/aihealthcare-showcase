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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link SsoRestController}.
 *
 * <p>REST CRUD for SSO Identity Providers. Requires ADMIN role.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
@Import(SecurityConfig.class)
@WithMockUser(username = "admin@test.com", roles = "ADMIN")
@WebMvcTest(SsoRestController.class)
class SsoRestControllerTest {

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
    void listProviders_returnsActiveProviders() throws Exception {
        when(ssoUseCase.getAllActive()).thenReturn(List.of(testProvider()));

        mockMvc.perform(get("/api/v1/sso/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].registrationId").value("acme-health"))
                .andExpect(jsonPath("$[0].label").value("Acme Health SSO"));
    }

    @Test
    void createProvider_returns201() throws Exception {
        when(ssoUseCase.create(any())).thenReturn(testProvider());

        String json = """
                {
                  "registrationId": "acme-health",
                  "label": "Acme Health SSO",
                  "entityId": "https://idp.acme.com/saml",
                  "ssoUrl": "https://idp.acme.com/saml/sso",
                  "certificate": "MIICdummycert",
                  "emailAttribute": "email",
                  "displayNameAttribute": "displayName",
                  "active": true
                }
                """;

        mockMvc.perform(post("/api/v1/sso/providers").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationId").value("acme-health"));

        verify(ssoUseCase).create(any(SsoIdentityProvider.class));
    }

    @Test
    void updateProvider_returns200() throws Exception {
        when(ssoUseCase.getById("acme-health")).thenReturn(Optional.of(testProvider()));
        when(ssoUseCase.update(any())).thenReturn(testProvider());

        String json = """
                {
                  "registrationId": "acme-health",
                  "label": "Acme Health SSO Updated",
                  "entityId": "https://idp.acme.com/saml",
                  "ssoUrl": "https://idp.acme.com/saml/sso",
                  "certificate": "MIICdummycert",
                  "emailAttribute": "email",
                  "displayNameAttribute": "displayName",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/sso/providers/acme-health").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationId").value("acme-health"));
    }

    @Test
    void deleteProvider_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/sso/providers/acme-health").with(csrf()))
                .andExpect(status().isNoContent());

        verify(ssoUseCase).delete("acme-health");
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void listProviders_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/sso/providers"))
                .andExpect(status().isForbidden());
    }
}
