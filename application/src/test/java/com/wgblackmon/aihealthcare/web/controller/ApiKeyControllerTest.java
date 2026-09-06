package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ApiKey;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link ApiKeyController}.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-03
 * @updated 2026-09-06
 */
@Import(SecurityConfig.class)
@WebMvcTest(ApiKeyController.class)
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private SubscriberPort subscriberPort;

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    private void stubUser(String email, SubscriptionTier tier) {
        Subscriber subscriber = new Subscriber(email, "Test User", true, NOW, tier, null, null, null);
        when(subscriberPort.findByEmail(email)).thenReturn(Optional.of(subscriber));
    }

    @Test
    @DisplayName("POST /api/v1/keys creates key for SUBSCRIBER")
    @WithMockUser(username = "user@test.com")
    void createKey_subscriber_returns201() throws Exception {
        stubUser("user@test.com", SubscriptionTier.SUBSCRIBER);
        when(apiKeyPort.countByOwnerEmail("user@test.com")).thenReturn(0);

        mockMvc.perform(post("/api/v1/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"My Test Key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My Test Key"))
                .andExpect(jsonPath("$.rawKey").value(startsWith("aih_")))
                .andExpect(jsonPath("$.active").value(true));

        verify(apiKeyPort).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("POST /api/v1/keys denied for FREE tier")
    @WithMockUser(username = "free@test.com")
    void createKey_freeTier_returns403() throws Exception {
        stubUser("free@test.com", SubscriptionTier.FREE);

        mockMvc.perform(post("/api/v1/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"My Key\"}"))
                .andExpect(status().isForbidden());

        verify(apiKeyPort, never()).save(any());
    }

    @Test
    @DisplayName("POST /api/v1/keys denied when key count exceeded")
    @WithMockUser(username = "user@test.com")
    void createKey_keyCountExceeded_returns403() throws Exception {
        stubUser("user@test.com", SubscriptionTier.SUBSCRIBER);
        when(apiKeyPort.countByOwnerEmail("user@test.com")).thenReturn(3);

        mockMvc.perform(post("/api/v1/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Another Key\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/keys with blank name returns 400")
    @WithMockUser(username = "user@test.com")
    void createKey_blankName_returns400() throws Exception {
        stubUser("user@test.com", SubscriptionTier.SUBSCRIBER);

        mockMvc.perform(post("/api/v1/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/keys returns user's keys without raw key")
    @WithMockUser(username = "user@test.com")
    void listKeys_returnsUserKeys() throws Exception {
        ApiKey key = new ApiKey("k1", "user@test.com", "Key One", "aih_1234",
                               "hash123", true, NOW);
        when(apiKeyPort.findAllByOwnerEmail("user@test.com")).thenReturn(List.of(key));

        mockMvc.perform(get("/api/v1/keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Key One"))
                .andExpect(jsonPath("$[0].rawKey").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /api/v1/keys/{id} by owner returns 204")
    @WithMockUser(username = "user@test.com")
    void deleteKey_byOwner_returns204() throws Exception {
        ApiKey key = new ApiKey("k1", "user@test.com", "Key One", "aih_1234",
                               "hash123", true, NOW);
        when(apiKeyPort.findById("k1")).thenReturn(Optional.of(key));

        mockMvc.perform(delete("/api/v1/keys/k1"))
                .andExpect(status().isNoContent());

        verify(apiKeyPort).deleteById("k1");
    }

    @Test
    @DisplayName("DELETE /api/v1/keys/{id} by non-owner returns 403")
    @WithMockUser(username = "other@test.com")
    void deleteKey_byNonOwner_returns403() throws Exception {
        ApiKey key = new ApiKey("k1", "user@test.com", "Key One", "aih_1234",
                               "hash123", true, NOW);
        when(apiKeyPort.findById("k1")).thenReturn(Optional.of(key));

        mockMvc.perform(delete("/api/v1/keys/k1"))
                .andExpect(status().isForbidden());

        verify(apiKeyPort, never()).deleteById("k1");
    }

    @Test
    @DisplayName("DELETE /api/v1/keys/{id} by ADMIN bypasses ownership")
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void deleteKey_byAdmin_returns204() throws Exception {
        ApiKey key = new ApiKey("k1", "user@test.com", "Key One", "aih_1234",
                               "hash123", true, NOW);
        when(apiKeyPort.findById("k1")).thenReturn(Optional.of(key));

        mockMvc.perform(delete("/api/v1/keys/k1"))
                .andExpect(status().isNoContent());

        verify(apiKeyPort).deleteById("k1");
    }

    @Test
    @DisplayName("DELETE /api/v1/keys/{id} with unknown id returns 404")
    @WithMockUser(username = "user@test.com")
    void deleteKey_unknownId_returns404() throws Exception {
        when(apiKeyPort.findById("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/keys/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/keys for ENTERPRISE allows up to 10 keys")
    @WithMockUser(username = "ent@test.com")
    void createKey_enterprise_allowsHigherLimit() throws Exception {
        stubUser("ent@test.com", SubscriptionTier.ENTERPRISE);
        when(apiKeyPort.countByOwnerEmail("ent@test.com")).thenReturn(9);

        mockMvc.perform(post("/api/v1/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Key 10\"}"))
                .andExpect(status().isCreated());
    }
}
