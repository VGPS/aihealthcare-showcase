package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.RemoteAuthType;
import com.wgblackmon.aihealthcare.domain.model.RemoteConnection;
import com.wgblackmon.aihealthcare.domain.model.RemoteConnectionKind;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageRemoteConnectionsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link EnterpriseConnectionRestController}.
 *
 * <p>Validates CRUD operations and — critically — that the response body
 * <strong>never contains the secretRef value</strong>.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Import(SecurityConfig.class)
@WebMvcTest(EnterpriseConnectionRestController.class)
class EnterpriseConnectionRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageRemoteConnectionsUseCase useCase;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    private static final String OWNER = "enterprise@test.com";

    // ── Create ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("POST /api/v1/enterprise/connections → 201 + Location")
    void create_returns201() throws Exception {
        RemoteConnection conn = makeConnection("conn-1", "MY_API_KEY_ENV");
        when(useCase.create(any(RemoteConnection.class))).thenReturn(conn);

        mockMvc.perform(post("/api/v1/enterprise/connections")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label":"My Endpoint",
                                  "kind":"HTTPS_JSON",
                                  "baseUrl":"https://api.example.com/data",
                                  "authType":"API_KEY_HEADER",
                                  "headerName":"X-Api-Key",
                                  "secretRef":"MY_API_KEY_ENV",
                                  "active":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.connectionId").value("conn-1"))
                .andExpect(jsonPath("$.hasSecret").value(true));
    }

    // ── SECURITY: response never contains secretRef ─────────────────────

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("Response body never contains secretRef value")
    void responseNeverContainsSecretRef() throws Exception {
        RemoteConnection conn = makeConnection("conn-1", "SUPER_SECRET_ENV_VAR");
        when(useCase.create(any(RemoteConnection.class))).thenReturn(conn);

        String responseBody = mockMvc.perform(post("/api/v1/enterprise/connections")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label":"Test","kind":"HTTPS_JSON",
                                  "baseUrl":"https://api.example.com",
                                  "authType":"API_KEY_HEADER",
                                  "headerName":"X-Key",
                                  "secretRef":"SUPER_SECRET_ENV_VAR",
                                  "active":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(responseBody)
                .doesNotContain("SUPER_SECRET_ENV_VAR")
                .doesNotContain("secretRef");
    }

    // ── Get ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("GET /api/v1/enterprise/connections/{id} → 200")
    void get_exists_returns200() throws Exception {
        RemoteConnection conn = makeConnection("conn-1", null);
        when(useCase.get("conn-1", OWNER)).thenReturn(conn);

        mockMvc.perform(get("/api/v1/enterprise/connections/conn-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionId").value("conn-1"))
                .andExpect(jsonPath("$.hasSecret").value(false));
    }

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("GET /api/v1/enterprise/connections/{id} not found → 404")
    void get_notFound_returns404() throws Exception {
        when(useCase.get("missing", OWNER))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/api/v1/enterprise/connections/missing"))
                .andExpect(status().isNotFound());
    }

    // ── List ────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("GET /api/v1/enterprise/connections → 200 with list")
    void list_returns200() throws Exception {
        when(useCase.list(OWNER)).thenReturn(
                List.of(makeConnection("c1", null), makeConnection("c2", "ENV_KEY")));

        mockMvc.perform(get("/api/v1/enterprise/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].connectionId").value("c1"))
                .andExpect(jsonPath("$[1].hasSecret").value(true));
    }

    // ── Update ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("PUT /api/v1/enterprise/connections/{id} → 200")
    void update_exists_returns200() throws Exception {
        RemoteConnection updated = makeConnection("conn-1", "NEW_KEY");
        when(useCase.update(eq("conn-1"), eq(OWNER), any(RemoteConnection.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/enterprise/connections/conn-1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label":"Updated","kind":"HTTPS_JSON",
                                  "baseUrl":"https://api.example.com",
                                  "authType":"BEARER",
                                  "secretRef":"NEW_KEY",
                                  "active":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionId").value("conn-1"));
    }

    // ── Delete ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = OWNER)
    @DisplayName("DELETE /api/v1/enterprise/connections/{id} → 204")
    void delete_exists_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/enterprise/connections/conn-1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(useCase).delete("conn-1", OWNER);
    }

    // ── Unauthenticated ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/enterprise/connections unauthenticated → redirect")
    void unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/v1/enterprise/connections"))
                .andExpect(status().is3xxRedirection());
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private RemoteConnection makeConnection(String id, String secretRef) {
        return new RemoteConnection(
                id, OWNER, "Test Endpoint",
                RemoteConnectionKind.HTTPS_JSON,
                "https://api.example.com/data",
                RemoteAuthType.API_KEY_HEADER,
                "X-Api-Key",
                secretRef,
                true);
    }
}
